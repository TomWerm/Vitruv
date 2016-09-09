package tools.vitruv.framework.change.description.impl

import tools.vitruv.framework.change.echange.EChange
import tools.vitruv.framework.util.datatypes.VURI
import tools.vitruv.framework.change.description.ConcreteChange

class ConcreteChangeImpl extends AbstractConcreteChange implements ConcreteChange {
    public new(EChange eChange, VURI vuri) {
    	super(vuri);
        this.eChange = eChange;
    }

    public override String toString() {
        return ConcreteChangeImpl.getSimpleName() + ": VURI: " + this.URI + " EChange: " + this.eChange;
    }
				
	override prepare() {
		// Do nothing
	}
	
	override isPrepared() {
		return true;
	}

}