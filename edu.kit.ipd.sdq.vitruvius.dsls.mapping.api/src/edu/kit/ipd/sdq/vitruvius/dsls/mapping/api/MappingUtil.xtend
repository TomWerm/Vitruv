package edu.kit.ipd.sdq.vitruvius.dsls.mapping.api

import edu.kit.ipd.sdq.vitruvius.framework.util.datatypes.VURI
import edu.kit.ipd.sdq.vitruvius.framework.contracts.interfaces.ModelProviding
import edu.kit.ipd.sdq.vitruvius.dsls.mapping.api.MIRMappingHelper
import java.util.Collection
import java.util.List
import java.util.Objects
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.impl.BasicEObjectImpl
import edu.kit.ipd.sdq.vitruvius.framework.changes.echange.EChange

class MappingUtil {
	public static def boolean isEChangeInPackage(EChange eChange, EPackage ePackage) {
		val allAffectedObjects = MIRMappingHelper.getAllAffectedObjects(eChange);
		return allAffectedObjects.exists[it.eClass.getEPackage.equals(ePackage)]
	}
	

	public static def List<EObject> createSignature(EPackage ePackage, List<EClass> signature, MappingExecutionState state) {
		val result = newArrayList
		
		for (eClass : signature) {
			val newEObject = ePackage.getEFactoryInstance.create(eClass)
			state.addCreatedEObject(newEObject)
			result.add(newEObject)
		}
		
		return result
	}
	
	public static def boolean containsSameIdObject(Collection<? extends EObject> collection, EObject eObjectToFind) {
		collection.exists[it.id == eObjectToFind.id]
	}
	
	public static def String getId(EObject eObject) {
		val feature = eObject.eClass.getEStructuralFeature("id")
		Objects.requireNonNull(feature)
		
		return (eObject.eGet(feature) as String)
	}
	
	@Deprecated
	public static def <T extends EObject> T resolved(T potentialProxy, ModelProviding mp) {
		if (!potentialProxy.eIsProxy) {
			return potentialProxy
		} else {
			val proxyUri = (potentialProxy as BasicEObjectImpl).eProxyURI
			val vuri = VURI.getInstance(proxyUri.trimFragment)
			val modelInstance = mp.getAndLoadModelInstanceOriginal(vuri)
			
			val resolvedObject = modelInstance.resource.allContents.findFirst[it.id == proxyUri.fragment] as T
			return resolvedObject
		}
	}
}