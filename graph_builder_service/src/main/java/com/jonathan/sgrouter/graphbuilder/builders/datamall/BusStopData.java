package com.jonathan.sgrouter.graphbuilder.builders.datamall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BusStopData extends DatamallBus<String,BusStop> {
	@Override
	String initDatamallType() {
		return "BusStops";
	}

	@Override
	void processData(JSONArray value) {
		for (int i = 0; i < value.length(); i++) {
			try {
				JSONObject x = value.getJSONObject(i);
				output.put(x.getString("BusStopCode"), new BusStop(
					x.getString("Description"), 
					x.getDouble("Latitude"), 
					x.getDouble("Longitude")
				));
			} catch (JSONException e) {
				System.out.println(e);
			}
		}
	}
}
