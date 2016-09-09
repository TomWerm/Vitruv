package tools.vitruv.framework.change.description.impl

import tools.vitruv.framework.change.description.ConcreteChange
import tools.vitruv.framework.change.echange.EChange
import java.util.ArrayList
import tools.vitruv.framework.util.datatypes.VURI

abstract class AbstractConcreteChange implements ConcreteChange {
	protected EChange eChange;
	final VURI vuri;
	
	new(VURI vuri) {
		this.vuri = vuri;
	}
	
	override containsConcreteChange() {
		return true;
	}
	
	override validate() {
		return containsConcreteChange() && URI != null;
	}
	
	override getEChanges() {
		return new ArrayList<EChange>(#[eChange]);
	}
	
	override getURI() {
		return vuri;
	}
		
	override isPrepared() {
		return true;
	}

	override getEChange() {
		return eChange;
	}
	
}