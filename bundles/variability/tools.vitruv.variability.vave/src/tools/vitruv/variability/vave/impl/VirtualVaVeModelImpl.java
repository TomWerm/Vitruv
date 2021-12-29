package tools.vitruv.variability.vave.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;

import tools.vitruv.framework.change.description.VitruviusChange;
import tools.vitruv.framework.change.description.impl.TransactionalChangeImpl;
import tools.vitruv.framework.change.echange.EChange;
import tools.vitruv.framework.domains.VitruvDomain;
import tools.vitruv.framework.domains.repository.VitruvDomainRepository;
import tools.vitruv.framework.domains.repository.VitruvDomainRepositoryImpl;
import tools.vitruv.framework.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.propagation.ChangePropagationSpecificationRepository;
import tools.vitruv.framework.userinteraction.InteractionResultProvider;
import tools.vitruv.framework.userinteraction.InternalUserInteractor;
import tools.vitruv.framework.userinteraction.UserInteractionFactory;
import tools.vitruv.framework.vsum.helper.VsumFileSystemLayout;
import tools.vitruv.variability.vave.VirtualProductModel;
import tools.vitruv.variability.vave.VirtualProductModelInitializer;
import tools.vitruv.variability.vave.VirtualVaVeModel;
import tools.vitruv.variability.vave.util.ExpressionComparator;
import tools.vitruv.variability.vave.util.ExpressionCopier;
import tools.vitruv.variability.vave.util.ExpressionEvaluator;
import tools.vitruv.variability.vave.util.ExpressionValidator;
import tools.vitruv.variability.vave.util.OptionsCollector;
import vavemodel.BinaryExpression;
import vavemodel.Configuration;
import vavemodel.Conjunction;
import vavemodel.Constraint;
import vavemodel.CrossTreeConstraint;
import vavemodel.DeltaModule;
import vavemodel.Expression;
import vavemodel.Feature;
import vavemodel.FeatureOption;
import vavemodel.FeatureRevision;
import vavemodel.Mapping;
import vavemodel.Option;
import vavemodel.SystemRevision;
import vavemodel.Term;
import vavemodel.TreeConstraint;
import vavemodel.UnaryExpression;
import vavemodel.Variable;
import vavemodel.VavemodelFactory;
import vavemodel.util.VavemodelSwitch;

public class VirtualVaVeModelImpl implements VirtualVaVeModel {

	private VitruvDomainRepository domainRepository = null;
	private vavemodel.System system;
	private Resource resource;
	private final Set<ChangePropagationSpecification> changePropagationSpecifications = new HashSet<ChangePropagationSpecification>();
	private InteractionResultProvider irp;
	private VirtualProductModelInitializer vpmi = null;

	private Collection<Feature[]> hints = new ArrayList<>();

	public VirtualVaVeModelImpl(Set<VitruvDomain> domains, Set<ChangePropagationSpecification> changePropagationSpecifications, InteractionResultProvider irp, Path storageFolder) throws IOException {
		this(domains, changePropagationSpecifications, irp, storageFolder, null);
	}

	public VirtualVaVeModelImpl(Set<VitruvDomain> domains, Set<ChangePropagationSpecification> changePropagationSpecifications, InteractionResultProvider irp, Path storageFolder, VirtualProductModelInitializer vpmi) throws IOException {
		if (Files.exists(storageFolder.resolve("vavemodel.vave"))) {
			// load
			this.resource = new XMIResourceImpl();
			File source = new File(storageFolder.resolve("vavemodel.vave").toString());
			this.resource.load(new FileInputStream(source), new HashMap<Object, Object>());
			this.system = vavemodel.System.class.cast(this.resource.getContents().get(0));
		} else {
			// create
			Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
			Map<String, Object> m = reg.getExtensionToFactoryMap();
			m.put("vave", new XMIResourceFactoryImpl());
			ResourceSet resSet = new ResourceSetImpl();
			this.resource = resSet.createResource(URI.createFileURI(storageFolder.resolve("vavemodel.vave").toString()));
			this.system = VavemodelFactory.eINSTANCE.createSystem();
			this.resource.getContents().add(this.system);
			try {
				this.resource.save(Collections.EMPTY_MAP);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		this.domainRepository = new VitruvDomainRepositoryImpl(domains);

		this.changePropagationSpecifications.addAll(changePropagationSpecifications);

		this.irp = irp;

		this.vpmi = vpmi;
	}

	private void save() throws IOException {
		this.resource.save(Collections.EMPTY_MAP);
	}

	@Override
	public vavemodel.System getSystem() {
		return this.system;
	}

	public SystemRevision createNewSystemRevision(SystemRevision predecessor, int id) {
		SystemRevision newsysrev = VavemodelFactory.eINSTANCE.createSystemRevision();
		newsysrev.setRevisionID(id);
		if (predecessor != null) {
			predecessor.getSuccessors().add(newsysrev);
			newsysrev.getPredecessors().add(predecessor);
		}
		this.system.getSystemrevision().add(newsysrev);
		return newsysrev;
	}

	public void transferDomain(SystemRevision predecessor, SystemRevision newsysrev) {
		if (predecessor != null) {
			// enable the same feature options
			newsysrev.getEnablesoptions().addAll(predecessor.getEnablesoptions());

			// enable the same constraints
			newsysrev.getEnablesconstraints().addAll(predecessor.getEnablesconstraints());
		}
	}

	public void transferMappings(SystemRevision predecessor, SystemRevision newsysrev) {
		if (predecessor != null) {
			// create new mappings (where old system revision is replaced by new system revision) for every existing mapping with the old system revision in its expression
			for (Mapping oldMapping : new ArrayList<>(system.getMapping())) {
				Collection<Option> mappingOptions = new OptionsCollector().doSwitch(oldMapping.getExpression());
				if (mappingOptions.contains(predecessor)) {
					Mapping newMapping = VavemodelFactory.eINSTANCE.createMapping();
					newMapping.getDeltamodule().addAll(oldMapping.getDeltamodule());
					newMapping.setExpression(EcoreUtil.copy(oldMapping.getExpression()));

					VavemodelSwitch<Object> exprSwitch = new VavemodelSwitch<>() {
						@Override
						public <T extends Option> Collection<Option> caseBinaryExpression(BinaryExpression<T> e) {
							for (Term t : e.getTerm())
								doSwitch(t);
							return null;
						}

						@Override
						public <T extends Option> Collection<Option> caseUnaryExpression(UnaryExpression<T> e) {
							doSwitch(e.getTerm());
							return null;
						}

						@Override
						public <T extends Option> Collection<Option> caseVariable(Variable<T> v) {
							if (v.getOption() instanceof SystemRevision && v.getOption().equals(predecessor)) {
								Variable<SystemRevision> vcast = (Variable<SystemRevision>) v;
								vcast.setOption(newsysrev);
							}
							return null;
						}
					};
					exprSwitch.doSwitch(newMapping.getExpression());
					this.system.getMapping().add(newMapping);
				}
			}
		}
	}

	public VirtualProductModel externalizeProduct(Path storageFolder, Configuration configuration) throws IOException {

		// FIRST WE DO THE VITRUV STUFF

//		final VirtualModelProductImpl vsum = new VirtualModelProductBuilder().withStorageFolder(storageFolder)
//				.withDomainRepository(this.domainRepository)
//				.withUserInteractor(UserInteractionFactory.instance.createUserInteractor(UserInteractionFactory.instance.createPredefinedInteractionResultProvider(null)))
//				.buildAndInitialize();

//		final Set<ChangePropagationSpecification> changePropagationSpecifications = new HashSet<ChangePropagationSpecification>();

		InternalUserInteractor userInteractor = UserInteractionFactory.instance.createUserInteractor(this.irp);

		if (storageFolder == null)
			throw new IllegalArgumentException("No storage folder was configured!");

		if (userInteractor == null)
			throw new IllegalArgumentException("No user interactor was configured!");

		final ChangePropagationSpecificationRepository changeSpecificationRepository = new ChangePropagationSpecificationRepository(changePropagationSpecifications);
		for (final ChangePropagationSpecification changePropagationSpecification : changePropagationSpecifications) {
			{
				boolean containsSourceDomain = false;
				boolean containsTargetDomain = false;
				for (VitruvDomain domain : this.domainRepository) {
					if (domain.equals(changePropagationSpecification.getSourceDomain())) {
						containsSourceDomain = true;
					}
					if (domain.equals(changePropagationSpecification.getTargetDomain())) {
						containsTargetDomain = true;
					}
				}
				if (!containsTargetDomain)
					throw new IllegalArgumentException("The change propagation specification’s source domain ‹" + changePropagationSpecification.getSourceDomain() + "› has not been configured: " + changePropagationSpecification);
				if (!containsSourceDomain)
					throw new IllegalArgumentException("The change propagation specification’s target domain ‹" + changePropagationSpecification.getTargetDomain() + "› has not been configured: " + changePropagationSpecification);
			}
		}

		for (final ChangePropagationSpecification changePropagationSpecification_1 : this.changePropagationSpecifications) {
			changePropagationSpecification_1.setUserInteractor(userInteractor);
		}

		final VsumFileSystemLayout fileSystemLayout = new VsumFileSystemLayout(storageFolder);
		fileSystemLayout.prepare();

		final VirtualProductModelImpl vsum = new VirtualProductModelImpl(configuration, fileSystemLayout, userInteractor, this.domainRepository, changeSpecificationRepository);
		vsum.loadExistingModels();
		// VirtualModelManager.getInstance().putVirtualModel(vsum);

		if (this.vpmi != null)
			this.vpmi.initialize(vsum);

		// HERE STARTS THE VAVE STUFF

		// optional: add configuration to unified system
		// this.system.getConfiguration().add(configuration);

		ExpressionEvaluator ee = new ExpressionEvaluator(configuration);

		for (Mapping mapping : this.system.getMapping()) {
			System.out.println("Evaluating Mapping: " + mapping.getExpression() + " : " + new OptionsCollector().doSwitch(mapping.getExpression()).stream().map(o -> {
				if (o instanceof FeatureRevision)
					return (((Feature) ((FeatureRevision) o).eContainer()).getName() + "." + ((FeatureRevision) o).getRevisionID());
				else
					return o.toString();
			}).collect(Collectors.joining(", ")));
			// retrieve only these delta modules whose mapping evaluates to true
			if (mapping.getExpression() == null || ee.eval(mapping.getExpression())) {
				System.out.println("Selected Mapping: " + mapping.getExpression() + " : " + new OptionsCollector().doSwitch(mapping.getExpression()).stream().map(Object::toString).collect(Collectors.joining(", ")));
				for (DeltaModule deltamodule : mapping.getDeltamodule()) {
					// EStructuralFeature eStructFeature = deltamodule.eClass().getEStructuralFeature("change");
					// echanges.add((EChange) deltamodule.eGet(eStructFeature));
					// One VitruviusChange per DeltaModule
					VitruviusChange vitruvchange = new TransactionalChangeImpl(deltamodule.getChange());
					vsum.propagateChange(vitruvchange);
				}
			}
		}
		vsum.clearDeltas();

		// check if any of the hinted feature interactions are contained in the configuration. if yes, then add a hint to the product.
		for (Feature[] featureInteraction : this.hints) {
			if (configuration.getOption().containsAll(Arrays.asList(featureInteraction))) {
				vsum.addHint(featureInteraction);
				System.out.println("RELEVANT HINT: " + Arrays.asList(featureInteraction));
			}
		}

		return vsum;
	}

	public FeatureModel internalizeChanges(VirtualProductModel virtualProductModel, Expression<FeatureOption> expression) throws IOException {
		// NOTE: for now we treat the expression provided by the user as a simple set of features. we ignore negations, disjunctions, feature revisions, etc.

		// NOTE: the following could be achieved with an OCL constraint
		boolean expressionIsWellFormed = new ExpressionValidator().doSwitch(expression);
		if (!expressionIsWellFormed) {
			throw new IllegalArgumentException("Expression is not well formed");
		}

		Collection<Option> options = new OptionsCollector().doSwitch(expression);

		// check if the expression covers any hints. if yes, then delete the respective hints.
		// NOTE: we can do this easily with the OptionCollector as long as we assume that expressions are always conjunctions of positive features (see NOTE at the top of this method)!
		Iterator<Feature[]> hintsIt = this.hints.iterator();
		while (hintsIt.hasNext()) {
			Feature[] featureInteraction = hintsIt.next();
			if (options.containsAll(Arrays.asList(featureInteraction))) {
				hintsIt.remove();
				System.out.println("FIXED HINT: " + Arrays.asList(featureInteraction));
			}
		}

		// create a new system revision and link it to predecessor system revision
		SystemRevision tempcursysrev = null;
		if (!system.getSystemrevision().isEmpty()) {
			tempcursysrev = this.system.getSystemrevision().get(this.system.getSystemrevision().size() - 1); // TODO add branch (by using sys rev of product) and merge points
		}
		final SystemRevision cursysrev = tempcursysrev;
		SystemRevision newsysrev = this.createNewSystemRevision(cursysrev, cursysrev != null ? cursysrev.getRevisionID() + 1 : 1);

		if (cursysrev != null) {
			// enable same features and feature revisions as the current system revision also in the new system revision, except those for which a new feature revision was created.
			for (FeatureOption featureoption : cursysrev.getEnablesoptions()) {
				if (!(featureoption instanceof FeatureRevision) || !options.contains(featureoption.eContainer()))
					newsysrev.getEnablesoptions().add(featureoption);
			}

			// enable same constraints as the current system revision
			newsysrev.getEnablesconstraints().addAll(cursysrev.getEnablesconstraints());
		}

		this.transferMappings(cursysrev, newsysrev);

		// make sure all options in the expression are enabled by the system revision
		// TODO

		// create new feature revisions for features that appear in expression
		List<FeatureRevision> newFeatureRevisions = new ArrayList<>();
		for (Option option : options) {
			if (option instanceof Feature) {
				Feature feature = (Feature) option;
				FeatureRevision featurerev = VavemodelFactory.eINSTANCE.createFeatureRevision();
				if (feature.getFeaturerevision().isEmpty()) {
					featurerev.setRevisionID(1);
					feature.getFeaturerevision().add(featurerev);
				} else {
					FeatureRevision curfeaturerev = feature.getFeaturerevision().get(feature.getFeaturerevision().size() - 1);
					featurerev.setRevisionID(curfeaturerev.getRevisionID() + 1);
					featurerev.getPredecessors().add(curfeaturerev);
					curfeaturerev.getSuccessors().add(featurerev);
					feature.getFeaturerevision().add(featurerev);
				}
				feature.getFeaturerevision().add(featurerev); // set feature as container for feature revision
				newFeatureRevisions.add(featurerev);
				newsysrev.getEnablesoptions().add(featurerev); // enable feature revisions by new system revision
				if (!newsysrev.getEnablesoptions().contains(feature))
					newsysrev.getEnablesoptions().add(feature);
			}
		}

		// Every mapping receives a new expression such that it contains the new system revision, the new feature revisions and features, and add expression to mapping.

		{
			// create mapping for new fragments
			Mapping mapping = VavemodelFactory.eINSTANCE.createMapping();

			// create mapping expression
			Expression<Option> mappingExpression;
			if (newFeatureRevisions.isEmpty()) {
				Variable<Option> srvariable = VavemodelFactory.eINSTANCE.createVariable();
				srvariable.setOption(newsysrev);
				mappingExpression = srvariable;
			} else {
				// NOTE: for now, we assume the user provides expression consisting only of conjunctions of variables (features)
				Conjunction<Option> initialConjunction = VavemodelFactory.eINSTANCE.createConjunction();
				mappingExpression = initialConjunction;
				vavemodel.Variable<Option> expression_sysrev = VavemodelFactory.eINSTANCE.createVariable();
				expression_sysrev.setOption(newsysrev);
				initialConjunction.getTerm().add(expression_sysrev);
				Conjunction<Option> currentConjunction = initialConjunction;
				if (newFeatureRevisions.size() > 1) {
					for (int i = 0; i < newFeatureRevisions.size() - 1; i++) {
						Conjunction<Option> newConjunction = VavemodelFactory.eINSTANCE.createConjunction();
						FeatureRevision featrev = newFeatureRevisions.get(i);

						vavemodel.Variable<Option> expression_featurerev = VavemodelFactory.eINSTANCE.createVariable();
						expression_featurerev.setOption(featrev);
						newConjunction.getTerm().add(expression_featurerev);
						currentConjunction.getTerm().add(newConjunction);
						currentConjunction = newConjunction;
					}
				}

				// add last feature revision
				vavemodel.Variable<Option> expression_featurerev = VavemodelFactory.eINSTANCE.createVariable();
				expression_featurerev.setOption(newFeatureRevisions.get(newFeatureRevisions.size() - 1));
				currentConjunction.getTerm().add(expression_featurerev);
			}

			// set expression of mapping
			mapping.setExpression(mappingExpression);

			// add mapping to us
			this.system.getMapping().add(mapping);

			// set fragments (i.e., deltas recorded in product) of mapping
			for (VitruviusChange change : virtualProductModel.getDeltas()) {
				VitruviusChange unresolvedChange = change.unresolve();
				DeltaModule dm = VavemodelFactory.eINSTANCE.createDeltaModule();
				for (EChange echange : unresolvedChange.getEChanges()) {
					dm.getChange().add(EcoreUtil.copy(echange)); // NOTE: this copy operation might be unnecessary
				}
				this.system.getDeltamodule().add(dm);
				mapping.getDeltamodule().add(dm);
			}

			// clear deltas in product
			virtualProductModel.clearDeltas();
		}

		this.save();

		return new DependencyLifting().liftingDependenciesBetweenFeatures(this, newsysrev);
	}

	public FeatureModel externalizeDomain(SystemRevision sysrev) {
		if (sysrev != null && sysrev.getEnablesoptions() == null) {
			throw new IllegalArgumentException("There are no enabled options by that system revision.");
		}
		if (sysrev == null) {
			return new FeatureModel(null, null, new HashSet<FeatureOption>(), new HashSet<TreeConstraint>(), new HashSet<CrossTreeConstraint>());
		}

		// copy all feature options and store them in two separate lists: one with only features and one with both features and feature revisions.
		HashMap<Feature, Feature> mapOriginalCopiedFeatures = new HashMap<Feature, Feature>();
		HashMap<FeatureOption, FeatureOption> mapOriginalCopiedLiterals = new HashMap<FeatureOption, FeatureOption>();
		for (FeatureOption fo : sysrev.getEnablesoptions()) {
			if (fo instanceof Feature) {
				Feature copiedFeature = VavemodelFactory.eINSTANCE.createFeature();
				copiedFeature.setName(((Feature) fo).getName());
				mapOriginalCopiedFeatures.put((Feature) fo, copiedFeature);
				mapOriginalCopiedLiterals.put(fo, copiedFeature);
			}
		}
		for (FeatureOption fo : sysrev.getEnablesoptions()) {
			if (fo instanceof FeatureRevision) {
				FeatureRevision copiedFO = VavemodelFactory.eINSTANCE.createFeatureRevision();
				copiedFO.setRevisionID(((FeatureRevision) fo).getRevisionID());
				mapOriginalCopiedFeatures.get(fo.eContainer()).getFeaturerevision().add(copiedFO);
				mapOriginalCopiedLiterals.put(fo, copiedFO);
			}
		}

		Set<TreeConstraint> treeConstraints = new HashSet<>();
		Set<CrossTreeConstraint> crossTreeConstraints = new HashSet<>();
		for (Constraint constraint : sysrev.getEnablesconstraints()) {
			// copy tree constraints setting parent (container) and children from previously copied features
			if (constraint instanceof TreeConstraint) {
				TreeConstraint copiedTC = VavemodelFactory.eINSTANCE.createTreeConstraint();
				copiedTC.setType(((TreeConstraint) constraint).getType());

				// find copied parent feature via original and set that copied feature as container
				Feature containingFeature = mapOriginalCopiedFeatures.get(constraint.eContainer());
				containingFeature.getTreeconstraint().add(copiedTC);
				for (Feature childFeature : ((TreeConstraint) constraint).getFeature()) {
					copiedTC.getFeature().add(mapOriginalCopiedFeatures.get(childFeature));
				}

				treeConstraints.add(copiedTC);
			} else if (constraint instanceof CrossTreeConstraint) {
				// copy cross tree constraints
				CrossTreeConstraint copiedCTC = VavemodelFactory.eINSTANCE.createCrossTreeConstraint();
				// copy CTC expression
				Term<FeatureOption> copiedExpression = ExpressionCopier.copy(((CrossTreeConstraint) constraint).getExpression());
				new VavemodelSwitch<Void>() {
					public <T extends Option> Void caseVariable(vavemodel.Variable<T> object) {
						object.setOption((T) mapOriginalCopiedLiterals.get(object.getOption()));
						return null;
					};
				}.doSwitch(copiedExpression);
				copiedCTC.setExpression((Expression<FeatureOption>) copiedExpression);

				crossTreeConstraints.add(copiedCTC);
			}
		}

		// obtain root feature for sysrev
		List<Feature> enabledRootFeatures = this.system.getFeature().stream().filter(p -> sysrev.getEnablesoptions().contains(p) && p.eContainer() instanceof vavemodel.System).collect(Collectors.toList());
//		if (enabledRootFeatures.size() > 1)
//			throw new Exception("More than one root feature is enabled by the system revision " + sysrev);
//		else if (enabledRootFeatures.size() < 1)
//			throw new Exception("No root feature is enabled by the system revision " + sysrev);
		Feature rootFeature = null;
		if (!enabledRootFeatures.isEmpty())
			rootFeature = enabledRootFeatures.get(0);

		FeatureModel featuremodel = new FeatureModel(rootFeature, sysrev, new HashSet<>(mapOriginalCopiedLiterals.values()), treeConstraints, crossTreeConstraints);

		return featuremodel;
	}

	public void internalizeDomain(FeatureModel fm) throws IOException {
		FeatureModel fmAtOldSysrev = null;

		// create a new system revision and link it to predecessor system revision
		SystemRevision tempcursysrev = null;
		if (!system.getSystemrevision().isEmpty()) {
			tempcursysrev = this.system.getSystemrevision().get(this.system.getSystemrevision().size() - 1); // TODO add branch (by using sys rev of product) and merge points
		}
		final SystemRevision cursysrev = tempcursysrev;
		SystemRevision newsysrev = this.createNewSystemRevision(cursysrev, cursysrev != null ? cursysrev.getRevisionID() + 1 : 1);

		this.transferMappings(cursysrev, newsysrev);

		// NOTE: fm stores (copy of) root node, ctcs, sysrev
		if (fm.getSysrev() == null) { // if we create the very first system revision, we don't need to do a diff
			newsysrev.getEnablesoptions().addAll(fm.getFeatureOptions());
			newsysrev.getEnablesconstraints().addAll(fm.getTreeConstraints());
			newsysrev.getEnablesconstraints().addAll(fm.getCrossTreeConstraints());

			// add very first features and constraints in fm to system
			// this.system.getFeature().add(fm.getRootFeature());
			// this.system.getFeature().addAll((Collection<? extends Feature>) unchangedFeatureOptions.stream().filter(p -> p instanceof Feature));
			Set<Feature> features = fm.getFeatureOptions().stream().filter(p -> p instanceof Feature).map(v -> (Feature) v).collect(Collectors.toSet());
			this.system.getFeature().addAll(features);
			if (features.size() != fm.getFeatureOptions().size())
				throw new IllegalArgumentException("It is not allowed to add new feature revisions to the domain manually!");
			this.system.getConstraint().addAll(fm.getCrossTreeConstraints());
		}

		else { // if we are here, there is at least one system revision and a feature model of that system revision
				// retrieve state of feature model before it was modified
			fmAtOldSysrev = this.externalizeDomain(fm.getSysrev());

			// diff the updated fm with the old fm

			Map<String, Feature> featureNameMap = new HashMap<>();

			// first do the feature options
			for (FeatureOption fo : fm.getFeatureOptions()) {
				// if it is a feature revision
				if (fo instanceof FeatureRevision) {
					// it must have existed before!
					Optional<FeatureRevision> oldFR = fmAtOldSysrev.getFeatureOptions().stream().filter(p -> p instanceof FeatureRevision && ((Feature) p.eContainer()).getName().equals(((Feature) fo.eContainer()).getName()) && ((FeatureRevision) p).getRevisionID() == ((FeatureRevision) fo).getRevisionID()).map(v -> (FeatureRevision) v).findAny();
					if (oldFR.isEmpty())
						throw new IllegalArgumentException("It is now allowed to add new feature revisions to the domain manually or use feature revisions that were not part of the previously externalized feature model!");
					// retrieve instances of feature and feature revision from unified system
					Optional<Feature> containingF = this.system.getFeature().stream().filter(p -> p instanceof Feature && ((Feature) p).getName().equals(((Feature) oldFR.get().eContainer()).getName())).map(v -> (Feature) v).findAny();
					if (containingF.isEmpty())
						throw new IllegalArgumentException("Feature does not exist in system!");
					Optional<FeatureRevision> containingFR = containingF.get().getFeaturerevision().stream().filter(p -> p.getRevisionID() == ((FeatureRevision) fo).getRevisionID()).findAny();
					if (containingFR.isEmpty())
						throw new IllegalArgumentException("Feature revision does not exist in feature!");
					// enable it by the new system revision
					newsysrev.getEnablesoptions().add(containingF.get());
					newsysrev.getEnablesoptions().add(containingFR.get());
				}
				// if it is a feature
				else if (fo instanceof Feature) {
					// check if it is new or existed before
					Optional<Feature> oldF = fmAtOldSysrev.getFeatureOptions().stream().filter(p -> p instanceof Feature && ((Feature) p).getName().equals(((Feature) fo).getName())).map(v -> (Feature) v).findAny();
					// if it is new
					if (oldF.isEmpty()) {
						// we need to create new instance for feature!
						Feature newF = VavemodelFactory.eINSTANCE.createFeature();
						newF.setName(((Feature) fo).getName());
						featureNameMap.put(newF.getName(), newF);
						// enable it by the new system revision
						newsysrev.getEnablesoptions().add(newF);
						this.system.getFeature().add(newF);
					}
					// if it is old
					else if (oldF.isPresent()) {
						// retrieve containing feature
						Optional<Feature> containingF = this.system.getFeature().stream().filter(p -> p instanceof Feature && ((Feature) p).getName().equals(oldF.get().getName())).map(v -> (Feature) v).findAny();
						if (containingF.isEmpty())
							throw new IllegalArgumentException("Feature does not exist in system: " + oldF.get().getName());
						// enable it by the new system revision
						newsysrev.getEnablesoptions().add(containingF.get());
						featureNameMap.put(containingF.get().getName(), containingF.get());
					}
				}
			}

			// next do the tree constraints
			for (TreeConstraint tc : fm.getTreeConstraints()) {
				Optional<TreeConstraint> oldTC = fmAtOldSysrev.getTreeConstraints().stream()
						.filter(p -> p.getType() == tc.getType() && ((Feature) p.eContainer()).getName().equals(((Feature) tc.eContainer()).getName()) && p.getFeature().size() == tc.getFeature().size() && p.getFeature().stream().allMatch(p2 -> tc.getFeature().stream().filter(p3 -> p3.getName().equals(p2.getName())).findAny().isPresent())).findAny();
				if (oldTC.isPresent()) {
					// retrieve containing feature instance from unified system
					Optional<Feature> containingF = this.system.getFeature().stream().filter(p -> p.getName().equals(((Feature) oldTC.get().eContainer()).getName())).findAny();
					if (containingF.isEmpty()) {
						throw new IllegalStateException("The containing feature of the constraint does, for some reason, not exist in the unified system!");
					} else {
						// retrieve instance of tree constraint from feature in the unified system
						Optional<TreeConstraint> containedTC = containingF.get().getTreeconstraint().stream().filter(p -> p.getType() == tc.getType() && p.getFeature().size() == tc.getFeature().size() && p.getFeature().stream().allMatch(p2 -> tc.getFeature().stream().filter(p3 -> p3.getName().equals(p2.getName())).findAny().isPresent())).findAny();
						// if the tree constraint is not a child of the same feature in the unified system, it means that it was moved as a child of another feature. in this case we also create a new instance of the constraint.
						if (containedTC.isEmpty()) {
							throw new IllegalStateException("The constraint existed in the externalized feature model but, for some reason, does not exist as a child of the same feature in the unified system!");
						}
						// the tree constraint was not modified in the externalized domain, the containing feature was found in the system, and the containing feature contained the same constraint (this case should always happen!)
						else {
							// enable it by new system revision
							newsysrev.getEnablesconstraints().add(containedTC.get());
						}
					}
				} else if (oldTC.isEmpty()) {
					// create new constraint
					TreeConstraint newTC = VavemodelFactory.eINSTANCE.createTreeConstraint();
					newTC.setType(tc.getType());
					Feature containedParentFeature = featureNameMap.get(((Feature) tc.eContainer()).getName());
					if (containedParentFeature == null)
						throw new IllegalArgumentException("Parent feature should have been contained in internalized FM but was not!");
					containedParentFeature.getTreeconstraint().add(newTC);
					for (Feature childFeature : tc.getFeature()) {
						Feature containedChildFeature = featureNameMap.get(childFeature.getName());
						if (containedChildFeature == null)
							throw new IllegalArgumentException("Child feature should have been contained in internalized FM but was not!");
						newTC.getFeature().add(containedChildFeature);
					}
					newsysrev.getEnablesconstraints().add(newTC);
				}
			}

			// finally do the cross tree constraints
			for (CrossTreeConstraint ctc : fm.getCrossTreeConstraints()) {
				Optional<CrossTreeConstraint> oldCTC = fmAtOldSysrev.getCrossTreeConstraints().stream().filter(oldCtc -> ExpressionComparator.equals(ctc.getExpression(), oldCtc.getExpression())).findAny();
				// if ctc already existed in the externalized view then it remained unchanged and will be enabled by the new system revision
				if (oldCTC.isPresent()) {
					Optional<CrossTreeConstraint> containedCTC = system.getConstraint().stream().filter(ct -> ExpressionComparator.equals(ctc.getExpression(), ct.getExpression())).findAny();
					if (containedCTC.isPresent())
						newsysrev.getEnablesconstraints().add(containedCTC.get());
					else
						throw new IllegalArgumentException("Could not find matching CTC in unified system!");
				}
				// if ctc did not exist in the externalized view then we create it and let the new system revision enable it
				else if (oldCTC.isEmpty()) {
					CrossTreeConstraint newCTC = VavemodelFactory.eINSTANCE.createCrossTreeConstraint();
					// copy CTC expression
					Term<FeatureOption> copiedExpression = ExpressionCopier.copy(ctc.getExpression());

					// replace feature option instances in expression with instances from vave model (unified system)
					new VavemodelSwitch<Void>() {
						public <T extends Option> Void caseVariable(vavemodel.Variable<T> object) {
							if (object.getOption() instanceof Feature) {
								object.setOption((T) system.getFeature().stream().filter(f -> Objects.equals(f.getName(), ((Feature) object.getOption().eContainer()).getName())).findAny().get());
							} else if (object.getOption() instanceof FeatureRevision) {
								Feature containedFeature = system.getFeature().stream().filter(f -> Objects.equals(f.getName(), ((Feature) object.getOption().eContainer()).getName())).findAny().get();
								object.setOption((T) containedFeature.getFeaturerevision().stream().filter(fr -> fr.getRevisionID() == ((FeatureRevision) object.getOption()).getRevisionID()).findAny().get());
							}
							return null;
						};
					}.doSwitch(copiedExpression);

					newCTC.setExpression((Expression<FeatureOption>) copiedExpression);
					this.system.getConstraint().add(newCTC);
					newsysrev.getEnablesconstraints().add(newCTC);
				}
			}
		}

		// inconsistency type 2 cause

		// obtain feature model at previous system revision
		FeatureModel fm_prev = fmAtOldSysrev;
		// obtain new feature model
		FeatureModel fm_cur = fm;

		Collection<FeatureOption> enabledFOs = newsysrev.getEnablesoptions();
		Collection<Feature> enabledFs = enabledFOs.stream().filter(fo -> fo instanceof Feature).map(f -> (Feature) f).collect(Collectors.toList());

		int currentInt = 0;
		Map<Option, Integer> optionToIntMap = new HashMap<>();
		for (FeatureOption fo : enabledFOs) {
			if (fo instanceof Feature) {
				optionToIntMap.put(fo, ++currentInt);
				System.out.println("ASSIGN VALUE " + optionToIntMap.get(fo) + " TO FEATURE " + fo);
			}
		}
		for (FeatureOption fo : enabledFOs) {
			if (fo instanceof FeatureRevision) {
				Feature feature = (Feature) ((FeatureRevision) fo).eContainer();
				if (optionToIntMap.get(feature) == null) {
					optionToIntMap.put(feature, ++currentInt);
					System.out.println("ASSIGN VALUE " + optionToIntMap.get(feature) + " TO FEATURE " + fo);
				}
				optionToIntMap.put(fo, optionToIntMap.get(feature));
				System.out.println("ASSIGN VALUE " + optionToIntMap.get(fo) + " TO FEATURE REVISION " + fo);
			}
		}

		Set<Option> notInOldFM = new HashSet<>();
		Collection<int[]> oldClauses = null;
		if (fm_prev == null) {
			oldClauses = new ArrayList<>();
			notInOldFM.addAll(enabledFOs);
		} else {
			Map<FeatureOption, Integer> oldfmOptionToIntMap = new HashMap<>();
			for (Entry<Option, Integer> entry : optionToIntMap.entrySet()) {
				if (entry.getKey() instanceof Feature) {
					Optional<Feature> fmFeature = fm_prev.getFeatureOptions().stream().filter(fo -> fo instanceof Feature).map(f -> (Feature) f).filter(f -> f.getName().equals(((Feature) entry.getKey()).getName())).findAny();
					if (fmFeature.isPresent())
						oldfmOptionToIntMap.put(fmFeature.get(), entry.getValue());
					else
						notInOldFM.add(entry.getKey());
				} else if (entry.getKey() instanceof FeatureRevision) {
					Optional<FeatureRevision> fmFeatureRevision = fm_prev.getFeatureOptions().stream().filter(fo -> fo instanceof FeatureRevision).map(fr -> (FeatureRevision) fr).filter(fr -> ((Feature) fr.eContainer()).getName().equals(((Feature) ((FeatureRevision) entry.getKey()).eContainer()).getName()) && fr.getRevisionID() == ((FeatureRevision) entry.getKey()).getRevisionID()).findAny();
					if (fmFeatureRevision.isPresent())
						oldfmOptionToIntMap.put(fmFeatureRevision.get(), entry.getValue());
					else
						notInOldFM.add(entry.getKey());
				}
			}
			oldClauses = fm_prev.computeClauses(oldfmOptionToIntMap);
		}

		Map<FeatureOption, Integer> newfmOptionToIntMap = new HashMap<>();
		for (Entry<Option, Integer> entry : optionToIntMap.entrySet()) {
			if (entry.getKey() instanceof Feature) {
				Feature fmFeature = fm_cur.getFeatureOptions().stream().filter(fo -> fo instanceof Feature).map(f -> (Feature) f).filter(f -> f.getName().equals(((Feature) entry.getKey()).getName())).findAny().get();
				newfmOptionToIntMap.put(fmFeature, entry.getValue());
			} else if (entry.getKey() instanceof FeatureRevision) {
				FeatureRevision fmFeatureRevision = fm_cur.getFeatureOptions().stream().filter(fo -> fo instanceof FeatureRevision).map(fr -> (FeatureRevision) fr).filter(fr -> ((Feature) fr.eContainer()).getName().equals(((Feature) ((FeatureRevision) entry.getKey()).eContainer()).getName()) && fr.getRevisionID() == ((FeatureRevision) entry.getKey()).getRevisionID()).findAny().get();
				newfmOptionToIntMap.put(fmFeatureRevision, entry.getValue());
			}
		}
		Collection<int[]> newClauses = fm_cur.computeClauses(newfmOptionToIntMap);

		for (Feature f1 : enabledFs) {
			for (Feature f2 : enabledFs) {
				ISolver oldFMSolver = SolverFactory.newDefault();
				ISolver newFMSolver = SolverFactory.newDefault();
				boolean oldSat = false;
				boolean newSat = false;

				try {
					if (notInOldFM.contains(f1) || notInOldFM.contains(f2)) {
						oldSat = false;
					} else {
						for (int[] clause : oldClauses)
							oldFMSolver.addClause(new VecInt(clause));
						oldFMSolver.addClause(new VecInt(new int[] { optionToIntMap.get(f1) }));
						oldFMSolver.addClause(new VecInt(new int[] { optionToIntMap.get(f2) }));
						oldSat = oldFMSolver.isSatisfiable();
					}
				} catch (ContradictionException e) {
					oldSat = false;
				} catch (TimeoutException e) {
					oldSat = false;
				}

				try {
					for (int[] clause : newClauses)
						newFMSolver.addClause(new VecInt(clause));
					newFMSolver.addClause(new VecInt(new int[] { optionToIntMap.get(f1) }));
					newFMSolver.addClause(new VecInt(new int[] { optionToIntMap.get(f2) }));
					newSat = newFMSolver.isSatisfiable();
				} catch (ContradictionException e) {
					newSat = false;
				} catch (TimeoutException e) {
					newSat = false;
				}

				if (!oldSat && newSat) {
					// feature interaction is new
					this.hints.add(new Feature[] { f1, f2 });
					System.out.println("ADDED HINT: " + f1 + " / " + f2);
				}
			}
		}

		// TODO: suggest configuration(s) that cover(s) as many hints as possible? or with as few other features as possible?

		// TODO: also consider feature interactions with negative features?

		this.save();
	}

}
