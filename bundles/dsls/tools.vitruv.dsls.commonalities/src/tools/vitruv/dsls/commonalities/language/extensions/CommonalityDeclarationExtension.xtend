package tools.vitruv.dsls.commonalities.language.extensions

import edu.kit.ipd.sdq.activextendannotations.Utility
import static extension tools.vitruv.dsls.commonalities.language.extensions.CommonalitiesLanguageElementExtension.getContainingCommonalityFile
import tools.vitruv.dsls.commonalities.language.CommonalityDeclaration

@Utility package class CommonalityDeclarationExtension {
	
	def static getConceptName(CommonalityDeclaration commonality) {
		commonality.containingCommonalityFile.concept.name
	} 
}