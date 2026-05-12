package de.cesr.crafty.core.updaters;


import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;

public class CellsUpdater extends AbstractUpdater {

//	public static ConcurrentHashMap<Cell, Aft> decesionsNewOwner = new ConcurrentHashMap<>();

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
//		changeOwners();
//		decesionsNewOwner.clear();
	}

//	private void changeOwners() {
//		decesionsNewOwner.forEach((c, newOwner) -> {
//			if (newOwner == null || newOwner.getLabel().equals("Abandoned")) {
//				c.setOwner(null);
//			} else {
//				c.setOwner(newOwner);
//			}
//		});
//	}

}
