/* *********************************************************************** *
 * project: org.matsim.*
 * FrameSaver.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package playground.gregor.sim2d_v4.debugger.eventsbaseddebugger;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import processing.core.PApplet;

public class FrameSaver {
	
	private final CyclicBarrier barrier = new CyclicBarrier(2);
	private final String path;
	private final String extension;
	private final int frameSkip;
	private int skiped;

	public FrameSaver(String path, String extension, int frameSkip) {
		this.path = path;
		this.extension = extension;
		this.frameSkip = frameSkip;
		this.skiped = frameSkip;
	}
	
//	public boolean skipNext() {
//		if (this.skiped == this.frameSkip) {
//			return true;
//		}
//		this.skiped++;
//		return false;
//	}
	
	public void saveFrame(PApplet p, String identifier) {
		if (this.skiped != this.frameSkip) {
			this.skiped++;
			this.await();
			return;
		}
		this.skiped = 0;
		StringBuffer bf = new StringBuffer();
		bf.append(this.path);
		bf.append("/");
		bf.append(identifier);
		bf.append(".");
		bf.append(this.extension);
		p.saveFrame(bf.toString());
		this.await();
	}
	
	public void await() {
		try {
			this.barrier.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (BrokenBarrierException e) {
			e.printStackTrace();
		}
	}

}
