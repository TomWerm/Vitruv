package edu.kit.ipd.sdq.vitruvius.dsls.response.generator.singleResponse

import edu.kit.ipd.sdq.vitruvius.dsls.response.generator.singleResponse.AbstractSingleResponseGenerator
import edu.kit.ipd.sdq.vitruvius.dsls.response.responseLanguage.Response
import edu.kit.ipd.sdq.vitruvius.dsls.response.responseLanguage.ModelChange
import org.eclipse.emf.ecore.EClass
import static extension edu.kit.ipd.sdq.vitruvius.dsls.response.helper.EChangeHelper.*;
import static edu.kit.ipd.sdq.vitruvius.dsls.response.generator.ResponseLanguageGeneratorConstants.*;
import static extension edu.kit.ipd.sdq.vitruvius.dsls.response.generator.ResponseLanguageGeneratorUtils.*;
import edu.kit.ipd.sdq.vitruvius.framework.meta.change.EChange
import edu.kit.ipd.sdq.vitruvius.framework.contracts.datatypes.Blackboard
import edu.kit.ipd.sdq.vitruvius.dsls.response.responseLanguage.ConcreteModelRootCreate
import edu.kit.ipd.sdq.vitruvius.dsls.response.responseLanguage.ConcreteModelRootUpdate

abstract class SingleModelChangeResponseGenerator extends AbstractSingleResponseGenerator {
	
	protected new(Response response) {
		super(response);
		if (!(response.trigger instanceof ModelChange)) {
			throw new IllegalArgumentException("Response must be a model change event")
		}
	}

	/**
	 * Generates method: performResponseTo : void
	 * 
	 * <p>Executes the response logic.
	 * 
	 * <p>Method parameters are:
	 * 	<li>1. change: the change event ({@link EChange})</li>
	 *  <li>2. targetModel: the target model root element if specified and if it is an update operation</li>
	 * 
	 * <p>Precondition: Executed codeBlock must exist.
	 */
	protected def generateMethodPerformResponse(EClass targetModelElement) '''
		private def performResponseTo(«changeEventTypeString» «CHANGE_PARAMETER_NAME»«
			IF hasTargetChange», «ih.typeRef(targetModelElement)
			» «TARGET_MODEL_PARAMETER_NAME»«ENDIF»)«response.effects.codeBlock.xtendCode»
	'''
	
	/**
	 * Generates method: performResponseTo : void
	 * 
	 * <p>Executes the response logic.
	 * 
	 * <p>Method parameters are:
	 * 	<li>1. change: the change event ({@link EChange})</li>
	 *  <li>2. blackboard: the {@link Blackboard} containing the {@link CorrespondenceInstance}</li>
	 * 
	 * <p>Precondition: Executed codeBlock must exist.
	 */
	protected def generateMethodPerformResponse() '''
		private def performResponseTo(«changeEventTypeString» «CHANGE_PARAMETER_NAME»«
			IF hasTargetChange», «ih.typeRef(Blackboard)
			» blackboard«ENDIF»)«response.effects.codeBlock.xtendCode»
	'''
	
	/**
	 * Generates method: checkChangeType : boolean
	 * 
	 * <p>Checks if the change type is correct.
	 * 
	 * <p>Method parameters are:
	 *  <li>1. change: the change event ({@link EChange})</li>
	 */
	protected def generateMethodCheckChangeType() '''
		private def boolean checkChangeType(«ih.typeRef(EChange)» «CHANGE_PARAMETER_NAME») { 
			return «CHANGE_PARAMETER_NAME» instanceof «ih.typeRef(change)»«IF !change.instanceClass.equals(EChange)»<?>«ENDIF»;
		}
	'''
	
	/**
	 * Generates: executeResponse
	 * 
	 * <p>Calls the response execution after checking preconditions for updating the determined target models.
	 * 
	 * <p>Methods parameters are:
	 * <li>1. change: the change event ({@link EChange})
	 * <li>2. blackboard: the {@link Blackboard} containing the {@link CorrespondenceInstance}
	 */
	protected def dispatch generateMethodExecuteResponse(ConcreteModelRootUpdate modelRootUpdate) '''
		private def executeResponse(«changeEventTypeString» «CHANGE_PARAMETER_NAME», «ih.typeRef(Blackboard)» blackboard) {
			val targetModels = determineTargetModels(«CHANGE_PARAMETER_NAME», blackboard);
			for (targetModel : targetModels) {
				LOGGER.debug("Execute response " + this.class.name + " for model " + targetModel);
				«IF hasExecutionBlock»
					performResponseTo(«CHANGE_PARAMETER_NAME», targetModel);
				«ENDIF»
			}
		}
	'''
	
	/**
	 * Generates: executeResponse
	 * 
	 * <p>Calls the response execution after checking preconditions for creating the target model.
	 * 
	 * <p>Methods parameters are:
	 * <li>1. change: the change event ({@link EChange})
	 * <li>2. blackboard: the {@link Blackboard} containing the {@link CorrespondenceInstance}
	 */
	protected def dispatch generateMethodExecuteResponse(ConcreteModelRootCreate modelRootCreate) '''
		private def executeResponse(«changeEventTypeString» «CHANGE_PARAMETER_NAME», «ih.typeRef(Blackboard)» blackboard) {
			val targetModel = generateTargetModel(«CHANGE_PARAMETER_NAME», blackboard);
			LOGGER.debug("Execute response " + this.class.name + " for new model " + targetModel);
			«IF hasExecutionBlock»
				performResponseTo(«CHANGE_PARAMETER_NAME», targetModel);
			«ENDIF»
		}
	'''
	
	/**
	 * Generates: executeResponse
	 * 
	 * <p>Calls the response execution after checking preconditions without a target model.
	 * 
	 * <p>Methods parameters are:
	 * <li>1. change: the change event ({@link EChange})
	 */
	protected def generateMethodExecuteResponse() '''
		private def executeResponse(«changeEventTypeString» «CHANGE_PARAMETER_NAME», «ih.typeRef(Blackboard)» blackboard) {
			LOGGER.debug("Execute response " + this.class.name + " with no affected model");
			«IF hasExecutionBlock»
				performResponseTo(«CHANGE_PARAMETER_NAME», blackboard);
			«ENDIF»
		}
	'''
	
	
	protected override EClass calculateEChange() {
		val changeEvent = response.trigger as ModelChange;
		return changeEvent.generateEChange();
	}
}