/*
 * Copyright 2013-2014 pushbit <pushbit@gmail.com>
 * 
 * This file is part of Dining Out.
 * 
 * Dining Out is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Dining Out is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Dining Out. If not,
 * see <http://www.gnu.org/licenses/>.
 */

package net.sf.diningout.sql;

import java.io.IOException;

import org.apache.commons.dbutils.QueryLoader;

/**
 * Get queries defined in the XML files in this package.
 */
public class Queries {
	private static final QueryLoader sLoader = QueryLoader.instance();
	private static final String FILE = file("queries");

	private Queries() {
	}

	/**
	 * Get the query identified by the key.
	 * 
	 * @throws IOException
	 *             if there is a problem reading the file
	 */
	public static String get(String key) throws IOException {
		return sLoader.load(FILE).get(key);
	}

	/**
	 * Get the full file path for the name without extension.
	 */
	private static String file(String name) {
		return '/' + Queries.class.getPackage().getName().replace('.', '/') + '/' + name + ".xml";
	}
}
