/**
 * Copyright 2010 Mozilla Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mozilla.testpilot.hive.serde;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.serde.Constants;
import org.apache.hadoop.hive.serde2.SerDe;
import org.apache.hadoop.hive.serde2.SerDeException;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

/**
 * TestPilot JSON is currently stored by Bagheera into HDFS.
 * 
 * You can create a table like so (EXTERNAL only please):
 * 
 * CREATE EXTERNAL TABLE IF NOT EXISTS study (key STRING, ts STRING, loc STRING, fxversion STRING, operatingsystem STRING, tpversion STRING, 
 *   event_headers ARRAY<STRING>, surveyanswers STRING, extensions MAP<STRING,BOOLEAN>, accessibilities MAP<STRING,STRING>, 
 *   preferences MAP<STRING,STRING>, events ARRAY<ARRAY<BIGINT>>) 
 *   ROW FORMAT SERDE 'com.mozilla.testpilot.hive.serde.TestPilotJsonSerde' LOCATION '/path/to/study';
 *   
 * This code is based on <a href="http://code.google.com/p/hive-json-serde/">hive-json-serde on Google Code</a>. The key difference is this class 
 * uses Jackson JSON Processor to deserialize/serialize the JSON strings, and is coded much more specifically to TestPilot data since it
 * is particularly complex JSON.
 * 
 */
public class TestPilotJsonSerde implements SerDe {

	private static final Log LOG = LogFactory.getLog(TestPilotJsonSerde.class.getName());
	
	/**
	 * An ObjectInspector to be used as meta-data about a deserialized row
	 */
	private StructObjectInspector rowOI;

	private ArrayList<Object> row;
	private int numColumns;
	private List<String> columnNames;
	private List<TypeInfo> columnTypes;

	private final ObjectMapper jsonMapper = new ObjectMapper();
	
	private Text serializedOutputValue = new Text();

	/**
	 * Initialize this SerDe with the system properties and table properties
	 * 
	 */
	@Override
	public void initialize(Configuration sysProps, Properties tblProps) throws SerDeException {
		LOG.debug("Initializing JsonSerde");

		// Get the names of the columns for the table this SerDe is being used
		// with
		String columnNameProperty = tblProps.getProperty(Constants.LIST_COLUMNS);
		columnNames = Arrays.asList(columnNameProperty.split(","));

		// Convert column types from text to TypeInfo objects
		String columnTypeProperty = tblProps.getProperty(Constants.LIST_COLUMN_TYPES);
		columnTypes = TypeInfoUtils.getTypeInfosFromTypeString(columnTypeProperty);
		assert columnNames.size() == columnTypes.size();
		numColumns = columnNames.size();

		// Create ObjectInspectors from the type information for each column
		List<ObjectInspector> columnOIs = new ArrayList<ObjectInspector>(columnNames.size());
		ObjectInspector oi;
		for (int c = 0; c < numColumns; c++) {
			oi = TypeInfoUtils.getStandardJavaObjectInspectorFromTypeInfo(columnTypes.get(c));
			columnOIs.add(oi);
		}
		rowOI = ObjectInspectorFactory.getStandardStructObjectInspector(columnNames, columnOIs);

		// Create an empty row object to be reused during deserialization
		row = new ArrayList<Object>(numColumns);
		for (int c = 0; c < numColumns; c++) {
			row.add(null);
		}

		LOG.debug("JsonSerde initialization complete");
	}

	/**
	 * Gets the ObjectInspector for a row deserialized by this SerDe
	 */
	@Override
	public ObjectInspector getObjectInspector() throws SerDeException {
		return rowOI;
	}

	/**
	 * Deserialize a JSON Object into a row for the table
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Object deserialize(Writable blob) throws SerDeException {
		String rowText = ((Text) blob).toString();
		if (LOG.isDebugEnabled()) {
			LOG.debug("Deserialize row: " + rowText);
		}
		
		// Try parsing row into JSON object
		Map<String,Object> values = new HashMap<String, Object>();
		try {
			Map<String, Object> tempValues = jsonMapper.readValue(rowText, new TypeReference<Map<String,Object>>() { });
			
			// Metadata
			if (tempValues.containsKey("metadata")) {
				Map<String, Object> metadata = (Map<String,Object>)tempValues.get("metadata");
				Map<String,String> preferencesMap = new HashMap<String,String>();
				
				for (Map.Entry<String, Object> metaEntry : metadata.entrySet()) {
				    String key = metaEntry.getKey();
				    Object vo = metaEntry.getValue();
				    // Extensions
				    if ("extensions".equals(key)) {
				        List<Object> extensions = (List<Object>)vo;
	                    Map<String,Boolean> extensionMap = new HashMap<String,Boolean>();
	                    for (Object o : extensions) {
	                        Map<String, Object> ex = (Map<String,Object>)o;
	                        String id = (String)ex.get("id");
	                        Boolean isEnabled = (Boolean)ex.get("isEnabled");
	                        extensionMap.put(id, isEnabled);
	                    }
	                    values.put("extensions", extensionMap);
	                    
	                // Accessibilities
				    } else if ("accessibilities".equals(key)) {
				        List<Object> accessibilities = (List<Object>)vo;
	                    Map<String,String> accessibilityMap = new HashMap<String,String>();
	                    for (Object o : accessibilities) {
                            Map<String, Object> a = (Map<String,Object>)o;
                            String name = (String)a.get("name");
                            // Get a string value of everything since we have mixed types
                            String v = String.valueOf(a.get("value"));
                            accessibilityMap.put(name, v);
	                    }
	                    values.put("accessibilities", accessibilityMap);
	                // Hack preferences
				    } else if (key.startsWith("Preference")) {
				        String name = key.replace("Preference ", "");
                        preferencesMap.put(name.toLowerCase(), String.valueOf(metaEntry.getValue()));
				    } else if ("Sync configured".equals(key)) {
				        preferencesMap.put("sync.configured", String.valueOf(metaEntry.getValue()));
				    // Leave survey answers as a JSON value for now
				    } else if ("surveyAnswers".equals(key)) {
		                values.put("surveyanswers", jsonMapper.writeValueAsString(vo));
		            // location is a hive keyword
				    } else if ("location".equals(key)) {
				        values.put("loc", vo);
				    } else {
				        values.put(key.toLowerCase(), vo);
				    }
				}
				
				if (preferencesMap.size() > 0) {
					values.put("preferences", preferencesMap);
				}
			}
			
			// Events
			if (tempValues.containsKey("events")) {
				List<List<Long>> events = (List<List<Long>>)tempValues.get("events");
				values.put("events", events);
			}
			
		} catch(JsonParseException e) {
			LOG.error("JSON Parse Error", e);
		} catch(JsonMappingException e) {
			LOG.error("JSON Mapping Error", e);
		} catch (IOException e) {
			LOG.error("IOException during JSON parsing", e);
		}

		if (values.size() == 0) {
			return null;
		}
		
		// Loop over columns in table and set values
		for (int c = 0; c < numColumns; c++) {
			String colName = columnNames.get(c);
			TypeInfo ti = columnTypes.get(c);
			Object value = null;
			
			try {
				// Get type-safe JSON values
				if (ti.getTypeName().equalsIgnoreCase(Constants.DOUBLE_TYPE_NAME)) {
					value = Double.valueOf((String)values.get(colName));
				} else if (ti.getTypeName().equalsIgnoreCase(Constants.BIGINT_TYPE_NAME)) {
					value = Long.valueOf((String)values.get(colName));
				} else if (ti.getTypeName().equalsIgnoreCase(Constants.INT_TYPE_NAME)) {
					value = Integer.valueOf((String)values.get(colName));
				} else if (ti.getTypeName().equalsIgnoreCase(Constants.TINYINT_TYPE_NAME)) {
					value = Byte.valueOf((String)values.get(colName));
				} else if (ti.getTypeName().equalsIgnoreCase(Constants.FLOAT_TYPE_NAME)) {
					value = Float.valueOf((String)values.get(colName));
				} else if (ti.getTypeName().equalsIgnoreCase(Constants.BOOLEAN_TYPE_NAME)) {
					value = Boolean.valueOf((String)values.get(colName));
				} else {
					// Fall back, just get an object
					value = values.get(colName);
				}
			} catch (RuntimeException e) {
				LOG.error("Class cast error for column name: " + colName);
				Object o = values.get(colName);
				if (o != null) {
					LOG.error("Value was: " + o.toString());
				}
				throw new SerDeException(e);
			}
			
			if (value == null) {
				// If the column cannot be found, just make it a NULL value and
				// skip over it
				LOG.warn("Column '" + colName + "' not found in row: " + rowText.toString());
			}
			row.set(c, value);
		}

		return row;
	}

	@Override
	public Class<? extends Writable> getSerializedClass() {
		return serializedOutputValue.getClass();
	}

	/**
	 * Serializes a row of data into a JSON object
	 */
	@Override
	public Writable serialize(Object obj, ObjectInspector objInspector) throws SerDeException {
		try {
			serializedOutputValue.set(jsonMapper.writeValueAsString(obj));
		} catch (JsonGenerationException e) {
			LOG.error("JSON generation exception occurred", e);
			throw new SerDeException(e);
		} catch (JsonMappingException e) {
			LOG.error("JSON mapping exception occurred", e);
			throw new SerDeException(e);
		} catch (IOException e) {
			LOG.error("IOException occurred", e);
			throw new SerDeException(e);
		}
		
		return serializedOutputValue;
	}

}
