package tools.vitruv.dsls.commonalities.language.extensions

import edu.kit.ipd.sdq.activextendannotations.Utility
import tools.vitruv.dsls.commonalities.language.AttributeEqualitySpecification
import tools.vitruv.dsls.commonalities.language.AttributeReadSpecification
import tools.vitruv.dsls.commonalities.language.AttributeSetSpecification
import tools.vitruv.dsls.commonalities.language.CommonalityAttribute
import tools.vitruv.dsls.commonalities.language.CommonalityAttributeMapping
import tools.vitruv.dsls.commonalities.language.elements.Classifier
import tools.vitruv.dsls.commonalities.language.elements.WellKnownClassifiers

import static extension tools.vitruv.dsls.commonalities.language.extensions.CommonalitiesLanguageElementExtension.*
import static extension tools.vitruv.dsls.commonalities.language.extensions.ParticipationClassExtension.*

@Utility
package class CommonalityAttributeMappingExtension {

	@Pure
	def static dispatch Classifier getProvidedType(AttributeReadSpecification mapping) {
		mapping.attribute.type
	}

	@Pure
	def static dispatch Classifier getRequiredType(AttributeReadSpecification mapping) {
		WellKnownClassifiers.JAVA_OBJECT
	}

	def static dispatch Classifier getProvidedType(AttributeSetSpecification mapping) {
		WellKnownClassifiers.MOST_SPECIFIC_TYPE
	}

	def static dispatch Classifier getRequiredType(AttributeSetSpecification mapping) {
		mapping.attribute.type
	}

	@Pure
	def static dispatch Classifier getProvidedType(AttributeEqualitySpecification mapping) {
		mapping.attribute.type
	}

	@Pure
	def static dispatch Classifier getRequiredType(AttributeEqualitySpecification mapping) {
		mapping.attribute.type
	}

	def static getParticipation(CommonalityAttributeMapping mapping) {
		mapping.attribute.participationClass.participation
	}

	def static getDeclaringAttribute(CommonalityAttributeMapping mapping) {
		return mapping.getDirectContainer(CommonalityAttribute)
	}

	def static dispatch isWrite(AttributeSetSpecification mapping) {
		true
	}

	def static dispatch isWrite(AttributeReadSpecification mapping) {
		false
	}

	def static dispatch isWrite(AttributeEqualitySpecification mapping) {
		true
	}

	def static dispatch isRead(AttributeSetSpecification mapping) {
		false
	}

	def static dispatch isRead(AttributeReadSpecification mapping) {
		true
	}

	def static dispatch isRead(AttributeEqualitySpecification mapping) {
		true
	}
}
