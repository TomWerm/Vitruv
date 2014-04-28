package edu.kit.ipd.sdq.vitruvius.framework.contracts.datatypes;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;

import edu.kit.ipd.sdq.vitruvius.framework.contracts.util.datatypes.ClaimableHashMap;
import edu.kit.ipd.sdq.vitruvius.framework.contracts.util.datatypes.ClaimableMap;
import edu.kit.ipd.sdq.vitruvius.framework.meta.correspondence.Correspondence;
import edu.kit.ipd.sdq.vitruvius.framework.meta.correspondence.CorrespondenceFactory;
import edu.kit.ipd.sdq.vitruvius.framework.meta.correspondence.Correspondences;
import edu.kit.ipd.sdq.vitruvius.framework.meta.correspondence.EFeatureCorrespondence;
import edu.kit.ipd.sdq.vitruvius.framework.meta.correspondence.EObjectCorrespondence;
import edu.kit.ipd.sdq.vitruvius.framework.meta.correspondence.SameTypeCorrespondence;

// TODO move all methods that don't need direct instance variable access to some kind of util class
/**
 * Contains all correspondences for all model instances that conform to the metamodels of a give
 * mapping. The correspondences do not store any information on metamodels. Only correspondence
 * instances link to the metamodels. Therefore every elementA of a correspondence has to be an
 * instance of a metaclass of the first metamodel of the containing correspondence instance. And
 * every elementB of a correspondence has to be an instance of a metaclass of the second metamodel
 * of the containing correspondence instance.
 * 
 * @author kramerm
 * 
 */
public class CorrespondenceInstance extends ModelInstance {

    private static final Logger logger = Logger.getLogger(CorrespondenceInstance.class.getSimpleName());

    private Mapping mapping;
    private Correspondences correspondences;
    private ClaimableMap<String, Set<Correspondence>> tuid2CorrespondencesMap;
    private ClaimableMap<String, Set<EObject>> tuid2CorrespondingEObjectsMap;
    private ClaimableMap<FeatureInstance, Set<FeatureInstance>> featureInstance2CorrespondingFIMap;
    // the following map (tuid2CorrespondenceSetsWithComprisedFeatureInstanceMap) is only used to
    // correctly update the previous map (featureInstance2CorrespondingFIMap)
    private ClaimableMap<String, Set<Set<FeatureInstance>>> tuid2CorrespondenceSetsWithComprisedFeatureInstanceMap;

    public CorrespondenceInstance(final Mapping mapping, final VURI vuri, final Resource resource) {
        super(vuri, resource);
        this.mapping = mapping;
        this.correspondences = CorrespondenceFactory.eINSTANCE.createCorrespondences();
        // TODO implement lazy loading for correspondences because they may get really big
        this.tuid2CorrespondencesMap = new ClaimableHashMap<String, Set<Correspondence>>();
        this.tuid2CorrespondingEObjectsMap = new ClaimableHashMap<String, Set<EObject>>();
        this.featureInstance2CorrespondingFIMap = new ClaimableHashMap<FeatureInstance, Set<FeatureInstance>>();
        this.tuid2CorrespondenceSetsWithComprisedFeatureInstanceMap = new ClaimableHashMap<String, Set<Set<FeatureInstance>>>();
        // TODO implement loading of existing correspondences from resources (fill maps)
        // TODO create TUIDs during loading of existing corresponding from resource
    }

    public Mapping getMapping() {
        return this.mapping;
    }

    public boolean hasCorrespondences(final EObject eObject) {
        String tuid = getTUIDFromEObject(eObject);
        return this.tuid2CorrespondencesMap.containsKey(tuid);
    }

    public Set<Correspondence> claimAllCorrespondences(final EObject eObject) {
        String tuid = getTUIDFromEObject(eObject);
        return this.tuid2CorrespondencesMap.claimValueForKey(tuid);
    }

    public boolean hasCorrespondingEObjects(final EObject eObject) {
        String tuid = getTUIDFromEObject(eObject);
        return this.tuid2CorrespondingEObjectsMap.containsKey(tuid);
    }

    public Set<EObject> claimCorrespondingEObjects(final EObject eObject) {
        String tuid = getTUIDFromEObject(eObject);
        return this.tuid2CorrespondingEObjectsMap.claimValueForKey(tuid);
    }

    public boolean isCorrespondingEObjectUnique(final EObject eObject) {
        String tuid = getTUIDFromEObject(eObject);
        return this.tuid2CorrespondingEObjectsMap.containsKey(tuid)
                && 1 == this.tuid2CorrespondingEObjectsMap.get(eObject).size();
    }

    public EObject claimCorrespondingEObjectIfUnique(final EObject eObject) {
        String tuid = getTUIDFromEObject(eObject);
        Set<EObject> correspondingEObjects = this.tuid2CorrespondingEObjectsMap.claimValueForKey(tuid);
        if (1 != correspondingEObjects.size()) {
            throw new RuntimeException("claimCorrespondingEObjectIfUnique failed: " + correspondingEObjects.size()
                    + " corresponding objects found (expected 1)" + correspondingEObjects);
        }
        return correspondingEObjects.iterator().next();
    }

    public <T> Set<T> claimCorredpondingEObjectsByType(final EObject eObject, final Class<T> type) {
        Set<EObject> correspondingEObjects = claimCorrespondingEObjects(eObject);
        Set<T> correspondingEObjectsByType = new HashSet<T>();
        for (EObject correspondingEObject : correspondingEObjects) {
            if (type.isInstance(correspondingEObject)) {
                correspondingEObjectsByType.add(type.cast(correspondingEObject));
            }
        }
        return correspondingEObjectsByType;
    }

    public <T> T claimCorrespondingEObjectByTypeIfUnique(final EObject eObject, final Class<T> type) {
        Set<T> correspondingEObjectsByType = this.claimCorredpondingEObjectsByType(eObject, type);
        if (1 != correspondingEObjectsByType.size()) {
            throw new RuntimeException("claimCorrespondingEObjectForTypeIfUnique failed: "
                    + correspondingEObjectsByType.size() + " corresponding objects found (expected 1)"
                    + correspondingEObjectsByType);
        }
        return correspondingEObjectsByType.iterator().next();
    }

    public Set<Correspondence> claimCorrespondencesForEObject(final EObject eObject) {
        String tuid = getTUIDFromEObject(eObject);
        if (!hasCorrespondenceObjects(eObject)) {
            this.tuid2CorrespondencesMap.put(tuid, new HashSet<Correspondence>());
        }
        return this.tuid2CorrespondencesMap.claimValueForKey(tuid);
    }

    public boolean hasCorrespondenceObjects(final EObject eObject) {
        String tuid = getTUIDFromEObject(eObject);
        return this.tuid2CorrespondencesMap.containsKey(tuid) && this.tuid2CorrespondencesMap.get(tuid).size() > 0;
    }

    public Correspondence getCorrespondeceForEObjectIfUnique(final EObject eObject) {
        if (!hasCorrespondenceObjects(eObject)) {
            return null;
        }
        Set<Correspondence> objectCorrespondences = claimCorrespondencesForEObject(eObject);
        if (1 != objectCorrespondences.size()) {
            throw new RuntimeException("claimCorrespondingEObjectForTypeIfUnique failed: "
                    + objectCorrespondences.size() + " corresponding objects found (expected 1)"
                    + objectCorrespondences);
        }
        return objectCorrespondences.iterator().next();
    }

    public void addSameTypeCorrespondence(final SameTypeCorrespondence correspondence) {
        // add TUIDs
        String tuidA = this.mapping.getMetamodelA().getTUID(correspondence.getElementA());
        String tuidB = this.mapping.getMetamodelB().getTUID(correspondence.getElementB());
        correspondence.setElementATUID(tuidA);
        correspondence.setElementBTUID(tuidB);
        // add correspondence to model
        this.correspondences.getCorrespondences().add(correspondence);
        EList<EObject> allInvolvedEObjects = correspondence.getAllInvolvedEObjects();
        List<String> allInvolvedTUIDs = Arrays.asList(tuidA, tuidB);
        // add all involved eObjects to the sets for these objects in the map
        for (String involvedTUID : allInvolvedTUIDs) {
            Set<Correspondence> correspondences = this.tuid2CorrespondencesMap.get(involvedTUID);
            if (correspondences == null) {
                correspondences = new HashSet<Correspondence>();
                this.tuid2CorrespondencesMap.put(involvedTUID, correspondences);
            }
            if (!correspondences.contains(correspondence)) {
                correspondences.add(correspondence);
            }
        }
        if (correspondence instanceof EObjectCorrespondence) {
            for (String involvedTUID : allInvolvedTUIDs) {
                Set<EObject> correspondingEObjects = this.tuid2CorrespondingEObjectsMap.get(involvedTUID);
                if (correspondingEObjects == null) {
                    correspondingEObjects = new HashSet<EObject>();
                    this.tuid2CorrespondingEObjectsMap.put(involvedTUID, correspondingEObjects);
                }
                correspondingEObjects.addAll(allInvolvedEObjects);
                correspondingEObjects.remove(involvedTUID);
            }
        } else if (correspondence instanceof EFeatureCorrespondence) {
            EFeatureCorrespondence<?> featureCorrespondence = (EFeatureCorrespondence<?>) correspondence;
            FeatureInstance featureInstanceA = FeatureInstance.getInstance(featureCorrespondence.getElementA(),
                    featureCorrespondence.getFeatureA());
            FeatureInstance featureInstanceB = FeatureInstance.getInstance(featureCorrespondence.getElementB(),
                    featureCorrespondence.getFeatureB());
            Set<FeatureInstance> featureInstancesCorrespondingToFIA = this.featureInstance2CorrespondingFIMap
                    .get(featureInstanceA);
            if (featureInstancesCorrespondingToFIA == null) {
                featureInstancesCorrespondingToFIA = new HashSet<FeatureInstance>();
                this.featureInstance2CorrespondingFIMap.put(featureInstanceA, featureInstancesCorrespondingToFIA);
            }
            featureInstancesCorrespondingToFIA.add(featureInstanceB);
            storeFeatureInstancesForTUID(tuidB, featureInstancesCorrespondingToFIA);

            Set<FeatureInstance> featureInstancesCorrespondingToFIB = this.featureInstance2CorrespondingFIMap
                    .get(featureInstanceB);
            if (featureInstancesCorrespondingToFIB == null) {
                featureInstancesCorrespondingToFIB = new HashSet<FeatureInstance>();
                this.featureInstance2CorrespondingFIMap.put(featureInstanceB, featureInstancesCorrespondingToFIB);
            }
            featureInstancesCorrespondingToFIB.add(featureInstanceA);
            // store the usage of a feature instance with a parent object that has the tuid tuidA
            storeFeatureInstancesForTUID(tuidA, featureInstancesCorrespondingToFIB);
        }

    }

    private void storeFeatureInstancesForTUID(final String tuid,
            final Set<FeatureInstance> correspondenceSetWithFIofTUID) {
        Set<Set<FeatureInstance>> correspondeceSetsWithFIsOfTUID = this.tuid2CorrespondenceSetsWithComprisedFeatureInstanceMap
                .get(tuid);
        if (correspondeceSetsWithFIsOfTUID == null) {
            correspondeceSetsWithFIsOfTUID = new HashSet<Set<FeatureInstance>>();
            this.tuid2CorrespondenceSetsWithComprisedFeatureInstanceMap.put(tuid, correspondeceSetsWithFIsOfTUID);
        }
        correspondeceSetsWithFIsOfTUID.add(correspondenceSetWithFIofTUID);
    }

    public void removeAllCorrespondingInstances(final EObject eObject) {
        // TODO: Check if it is working
        Set<Correspondence> correspondencesForEObj = this.tuid2CorrespondencesMap.get(eObject);
        this.tuid2CorrespondencesMap.remove(eObject);
        for (Correspondence correspondence : correspondencesForEObj) {
            this.correspondences.getCorrespondences().remove(correspondence);
        }
        // FIXME: remove feature correspondences
    }

    public Set<FeatureInstance> getCorrespondingFeatureInstances(final EObject parentEObject,
            final EStructuralFeature feature) {
        FeatureInstance featureInstance = FeatureInstance.getInstance(parentEObject, feature);
        return getCorrespondingFeatureInstances(featureInstance);
    }

    public Set<FeatureInstance> getCorrespondingFeatureInstances(final FeatureInstance featureInstance) {
        return this.featureInstance2CorrespondingFIMap.get(featureInstance);
    }

    private String getTUIDFromEObject(final EObject eObject) {
        if (this.mapping.getMetamodelA().hasMetaclassInstance(eObject)) {
            return this.mapping.getMetamodelA().getTUID(eObject);
        }
        if (this.mapping.getMetamodelB().hasMetaclassInstance(eObject)) {
            return this.mapping.getMetamodelB().getTUID(eObject);
        }
        logger.warn("EObject: '" + eObject + "' is neither an instance of MM1 nor an instance of MM2");
        return null;
    }

    public void update(final EObject oldEObject, final EObject newEObject) {
        String oldTUID = getTUIDFromEObject(oldEObject);
        String newTUID = getTUIDFromEObject(newEObject);
        boolean sameTUID = oldTUID != null ? oldTUID.equals(newTUID) : newTUID == null;
        updateTUID2CorrespondencesMap(oldEObject, newEObject, oldTUID, newTUID, sameTUID);

        updateTUID2CorrespondingEObjectsMap(oldTUID, newTUID, sameTUID);

        updateFeatureInstances(oldEObject, newEObject, oldTUID);
    }

    private void updateFeatureInstances(final EObject oldEObject, final EObject newEObject, String oldTUID) {
        Collection<FeatureInstance> oldFeatureInstances = FeatureInstance.getAllInstances(oldEObject);

        // WARNING: We assume that everybody that uses the FeatureInstance multiton wants to be
        // informed of this update
        FeatureInstance.update(oldEObject, newEObject);

        for (FeatureInstance oldFeatureInstance : oldFeatureInstances) {
            Set<FeatureInstance> correspondingFeatureInstances = this.featureInstance2CorrespondingFIMap
                    .remove(oldFeatureInstance);
            if (correspondingFeatureInstances != null) {
                EStructuralFeature feature = oldFeatureInstance.getFeature();
                FeatureInstance newFeatureInstance = FeatureInstance.getInstance(newEObject, feature);
                this.featureInstance2CorrespondingFIMap.put(newFeatureInstance, correspondingFeatureInstances);
            }
        }

        Set<Set<FeatureInstance>> correspondenceSetsWithFIofOldTUID = this.tuid2CorrespondenceSetsWithComprisedFeatureInstanceMap
                .get(oldTUID);
        for (Set<FeatureInstance> correspondenceSetWithFIofOldTUID : correspondenceSetsWithFIofOldTUID) {
            for (FeatureInstance featureInstance : correspondenceSetWithFIofOldTUID) {
                if (oldFeatureInstances.contains(featureInstance)) {
                    // featureInstance belongs to oldTUID
                    correspondenceSetWithFIofOldTUID.remove(featureInstance);
                    EStructuralFeature feature = featureInstance.getFeature();
                    FeatureInstance newFeatureInstance = FeatureInstance.getInstance(newEObject, feature);
                    correspondenceSetWithFIofOldTUID.add(newFeatureInstance);
                }
            }
        }
    }

    private void updateTUID2CorrespondingEObjectsMap(String oldTUID, String newTUID, boolean sameTUID) {
        if (!sameTUID) {
            Set<EObject> correspondingEObjects = this.tuid2CorrespondingEObjectsMap.remove(oldTUID);
            this.tuid2CorrespondingEObjectsMap.put(newTUID, correspondingEObjects);
        }
    }

    private void updateTUID2CorrespondencesMap(final EObject oldEObject, final EObject newEObject, String oldTUID,
            String newTUID, boolean sameTUID) {
        Set<Correspondence> correspondences = this.tuid2CorrespondencesMap.get(oldTUID);
        for (Correspondence correspondence : correspondences) {
            if (correspondence instanceof SameTypeCorrespondence) {
                SameTypeCorrespondence stc = (SameTypeCorrespondence) correspondence;
                if (oldTUID != null && oldTUID.equals(stc.getElementATUID())) {
                    stc.setElementA(newEObject);
                    if (!sameTUID) {
                        stc.setElementATUID(newTUID);
                    }

                    // update incoming links in tuid2CorrespondingEObjectsMap
                    String elementBTUID = stc.getElementBTUID();
                    updateCorrespondingLinksForUpdatedEObject(oldEObject, newEObject, oldTUID, elementBTUID);
                } else if (oldTUID != null && oldTUID.equals(stc.getElementBTUID())) {
                    stc.setElementB(newEObject);
                    if (!sameTUID) {
                        stc.setElementBTUID(newTUID);
                    }

                    // update incoming links in tuid2CorrespondingEObjectsMap
                    String elementATUID = stc.getElementATUID();
                    updateCorrespondingLinksForUpdatedEObject(oldEObject, newEObject, oldTUID, elementATUID);

                } else {
                    throw new RuntimeException("None of the corresponding elements in '" + correspondence
                            + "' has the TUID '" + oldTUID + "'!");
                }
                if (!sameTUID) {
                    this.tuid2CorrespondencesMap.remove(oldTUID);
                    this.tuid2CorrespondencesMap.put(newTUID, correspondences);
                }
            }
            // TODO handle not same type correspondence case
        }
    }

    private void updateCorrespondingLinksForUpdatedEObject(final EObject oldEObject, final EObject newEObject,
            final String oldTUID, final String elementBTUID) {
        Set<EObject> correspondingEObjects = this.tuid2CorrespondingEObjectsMap.get(elementBTUID);
        for (EObject correspondingEObject : correspondingEObjects) {
            String correspondingTUID = getTUIDFromEObject(correspondingEObject);
            if (correspondingTUID != null && correspondingTUID.equals(oldTUID)) {
                correspondingEObjects.remove(oldEObject);
                correspondingEObjects.add(newEObject);
            }
        }
    }
}
