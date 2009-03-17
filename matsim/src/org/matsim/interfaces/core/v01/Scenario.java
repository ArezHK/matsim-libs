package org.matsim.interfaces.core.v01;

import org.matsim.interfaces.basic.v01.BasicScenario;

public interface Scenario extends BasicScenario {

	public Network getNetwork() ;

	public Facilities getFacilities() ;

	public Population getPopulation() ;

}