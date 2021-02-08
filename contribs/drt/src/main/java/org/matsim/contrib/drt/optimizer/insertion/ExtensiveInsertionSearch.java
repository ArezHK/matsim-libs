/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.drt.optimizer.insertion;

import static org.matsim.contrib.drt.optimizer.insertion.InsertionCostCalculator.INFEASIBLE_SOLUTION_COST;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.matsim.contrib.drt.optimizer.VehicleEntry;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.path.OneToManyPathSearch.PathData;
import org.matsim.contrib.zone.skims.DvrpTravelTimeMatrix;
import org.matsim.core.mobsim.framework.MobsimTimer;

/**
 * @author michalm
 */
public class ExtensiveInsertionSearch implements DrtInsertionSearch<PathData> {
	private final ExtensiveInsertionSearchParams insertionParams;

	// step 1: initial filtering out feasible insertions
	private final InsertionCostCalculator<Double> admissibleCostCalculator;
	private final DetourTimeEstimator admissibleDetourTimeEstimator;

	// step 2: finding best insertion
	private final ForkJoinPool forkJoinPool;
	private final DetourPathCalculator detourPathCalculator;
	private final BestInsertionFinder<PathData> bestInsertionFinder;

	public ExtensiveInsertionSearch(DetourPathCalculator detourPathCalculator, DrtConfigGroup drtCfg, MobsimTimer timer,
			ForkJoinPool forkJoinPool, CostCalculationStrategy costCalculationStrategy,
			DvrpTravelTimeMatrix dvrpTravelTimeMatrix) {
		this.detourPathCalculator = detourPathCalculator;
		this.forkJoinPool = forkJoinPool;

		insertionParams = (ExtensiveInsertionSearchParams)drtCfg.getDrtInsertionSearchParams();
		admissibleDetourTimeEstimator = DetourTimeEstimator.createFreeSpeedZonalTimeEstimator(
				insertionParams.getAdmissibleBeelineSpeedFactor(), dvrpTravelTimeMatrix);
		admissibleCostCalculator = new InsertionCostCalculator<>(drtCfg, timer, costCalculationStrategy,
				Double::doubleValue, admissibleDetourTimeEstimator);

		bestInsertionFinder = new BestInsertionFinder<>(
				new InsertionCostCalculator<>(drtCfg, timer, costCalculationStrategy, PathData::getTravelTime, null));
	}

	@Override
	public Optional<InsertionWithDetourData<PathData>> findBestInsertion(DrtRequest drtRequest,
			Collection<VehicleEntry> vehicleEntries) {
		InsertionGenerator insertionGenerator = new InsertionGenerator();
		DetourData<Double> admissibleTimeData = DetourData.create(admissibleDetourTimeEstimator, drtRequest);

		// Parallel outer stream over vehicle entries. The inner stream (flatmap) is sequential.
		List<InsertionWithDetourData<Double>> preFilteredInsertions = forkJoinPool.submit(
				() -> vehicleEntries.parallelStream()
						//generate feasible insertions (wrt occupancy limits)
						.flatMap(e -> insertionGenerator.generateInsertions(drtRequest, e).stream())
						//map insertions to insertions with admissible detour times (i.e. admissible beeline speed factor)
						.map(admissibleTimeData::createInsertionWithDetourData)
						//optimistic pre-filtering wrt admissible cost function
						.filter(insertion -> admissibleCostCalculator.calculate(drtRequest, insertion)
								< INFEASIBLE_SOLUTION_COST)
						//collect
						.collect(Collectors.toList())).join();

		//TODO this may introduce non-determinism of results (add some additional ordering as in BestInsertionFinder)
		KNearestInsertionsAtEndFilter kNearestInsertionsAtEndFilter = new KNearestInsertionsAtEndFilter(
				insertionParams.getNearestInsertionsAtEndLimit(), insertionParams.getAdmissibleBeelineSpeedFactor());
		var filteredInsertions = preFilteredInsertions.stream()
				//skip insertions at schedule ends (a subset of most promising "insertionsAtEnd" will be added later)
				.filter(kNearestInsertionsAtEndFilter::filter)
				//forget (admissible) detour times
				.map(InsertionWithDetourData::getInsertion)
				//collect
				.collect(Collectors.toList());
		filteredInsertions.addAll(kNearestInsertionsAtEndFilter.getNearestInsertionsAtEnd());

		DetourData<PathData> pathData = detourPathCalculator.calculatePaths(drtRequest, filteredInsertions);
		//TODO could use a parallel stream within forkJoinPool, however the idea is to have as few filteredInsertions
		// as possible, and then using a parallel stream does not make sense.
		return bestInsertionFinder.findBestInsertion(drtRequest,
				filteredInsertions.stream().map(pathData::createInsertionWithDetourData));
	}
}
