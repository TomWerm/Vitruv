package tools.vitruv.framework.vsum.views.selection

import java.util.Collection
import org.eclipse.emf.ecore.EObject

import org.eclipse.xtend.lib.annotations.Accessors
import tools.vitruv.framework.vsum.views.ViewType
import tools.vitruv.framework.vsum.views.ChangeableViewSource

import static extension com.google.common.base.Preconditions.checkNotNull
import static com.google.common.base.Preconditions.checkState
import java.util.Map
import java.util.HashMap

/**
 * Basic view selector for a view that represents a set of model elements.
 * 
 * After creation, all elements will be unselected.
 */
class BasicViewSelector implements ViewSelector {

    val ViewType<BasicViewSelector> owner
    // TODO Discuss whether we want to guarantee some ordering in the elements. This was not document yet, but maybe we want to have it
    // TODO Remove protected modified, as they must not be used for instance variables --> Inheritance of AbstractTreeBasedViewSelector is incorrent
    protected val Map<EObject, Boolean> elementsSelection

	@Accessors(PUBLIC_GETTER)
	val ChangeableViewSource viewSource
	
    protected new(ViewType<BasicViewSelector> owner, ChangeableViewSource viewSource) {
        this.owner = owner
        this.viewSource = viewSource
        elementsSelection = new HashMap()
    }

    new(ViewType<BasicViewSelector> owner, ChangeableViewSource viewSource, Collection<EObject> elements) {
        this(owner, viewSource)
        elements.forEach[this.elementsSelection.put(checkNotNull(it), false)]
    }

    override getElements() {
        return elementsSelection.keySet
    }
    
    private def checkIsSelectable(EObject eObject) {
    	checkState(elementsSelection.keySet.contains(eObject), "Given object %s must be contained in the selector elements", eObject)
    }

    override setSelected(EObject eObject, boolean selected) {
    	eObject.checkIsSelectable
        elementsSelection.put(eObject, selected) 
    }

    override isSelected(EObject eObject) {
    	eObject.checkIsSelectable
        return elementsSelection.get(eObject)
    }

    override getSelectedElements() {
        return elementsSelection.filter[element, selected | selected == true].keySet
    }

    override createView() {
        return owner.createView(this)
    }

    /**
     * Checks if at least one element is selected.
     */
    override isValid() {
        return elementsSelection.values.contains(true)
    }

}
