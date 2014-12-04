/**
 * Copyright 2014, Auctionomics, Alexandre Fréchette, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.version;

import org.mangosdk.spi.ProviderFor;

import ca.ubc.cs.beta.aeatk.misc.version.AbstractVersionInfo;
import ca.ubc.cs.beta.aeatk.misc.version.VersionInfo;

@ProviderFor(VersionInfo.class)
public class SATFCVersionInfo extends AbstractVersionInfo {

	public SATFCVersionInfo() {
		super("SATFC", "satfc-version.txt", true);
	}

}