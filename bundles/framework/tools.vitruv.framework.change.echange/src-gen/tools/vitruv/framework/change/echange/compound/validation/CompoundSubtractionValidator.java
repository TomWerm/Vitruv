/**
 *
 * $Id$
 */
package tools.vitruv.framework.change.echange.compound.validation;

import org.eclipse.emf.common.util.EList;

import tools.vitruv.framework.change.echange.SubtractiveEChange;

/**
 * A sample validator interface for {@link tools.vitruv.framework.change.echange.compound.CompoundSubtraction}.
 * This doesn't really do anything, and it's not a real EMF artifact.
 * It was generated by the org.eclipse.emf.examples.generator.validator plug-in to illustrate how EMF's code generator can be extended.
 * This can be disabled with -vmargs -Dorg.eclipse.emf.examples.generator.validator=false.
 */
public interface CompoundSubtractionValidator {
	boolean validate();

	boolean validateSubtractiveChanges(EList<SubtractiveEChange> value);
}
