/*
 * generated by Xtext 2.12.0
 */
package tools.vitruv.dsls.mappings.ui.contentassist

import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.RuleCall
import org.eclipse.xtext.ui.editor.contentassist.ContentAssistContext
import org.eclipse.xtext.ui.editor.contentassist.ICompletionProposalAcceptor
import org.eclipse.xtext.Assignment
import org.eclipse.emf.ecore.util.EcoreUtil
import tools.vitruv.dsls.mappings.mappingsLanguage.Mapping

/**
 * See https://www.eclipse.org/Xtext/documentation/304_ide_concepts.html#content-assist
 * on how to customize the content assistant.
 */
class MappingsLanguageProposalProvider extends AbstractMappingsLanguageProposalProvider {
	 
	 	private def Mapping getMappingContainer(EObject object){
	 		val parent = object.eContainer
	 		if(parent instanceof Mapping){
	 			return parent
	 		}
	 		else{
	 			return getMappingContainer(parent)
	 		}
	 	}
	 
		override complete_Taggable(EObject model, RuleCall ruleCall, ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
			super.complete_Taggable(model, ruleCall, context, acceptor)
		}
		
		override complete_TagCodeBlock(EObject model, RuleCall ruleCall, ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
			val mapping = model.mappingContainer
			acceptor.accept(createCompletionProposal('test'+mapping.name,context))
		}
		
		override completeTaggable_Tag(EObject model, Assignment assignment, ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
			super.completeTaggable_Tag(model, assignment, context, acceptor)
		}
		
}
