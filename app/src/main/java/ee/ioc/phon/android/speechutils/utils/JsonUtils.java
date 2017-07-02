package ee.ioc.phon.android.speechutils.utils;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public final class JsonUtils {

    private JsonUtils() {
    }

    /**
     * Parse JSON.
     *
     * @param chars CharSequence that corresponds to serialized JSON.
     * @return JSONObject
     * @throws JSONException if parsing fails
     */
    public static JSONObject parseJson(CharSequence chars) throws JSONException {
        if (chars == null) {
            throw new JSONException("input is NULL");
        }
        return new JSONObject(chars.toString());
    }

    /**
     * TODO: support: broadcast intent, voice interaction launch mode, etc.
     *
     * @param query Intent serialized as JSON
     * @return Deserialized intent
     * @throws JSONException if parsing fails
     */
    public static Intent createIntent(CharSequence query) throws JSONException {
        JSONObject json = parseJson(query.toString());
        Intent intent = new Intent();
        String action = json.optString("action", null);
        if (action != null) {
            intent.setAction(action);
        }
        String component = json.optString("component", null);
        if (component != null) {
            intent.setComponent(ComponentName.unflattenFromString(component));
        }
        String packageName = json.optString("package", null);
        if (component != null) {
            intent.setPackage(packageName);
        }
        String data = json.optString("data", null);
        if (data != null) {
            intent.setData(Uri.parse(data));
        }
        String type = json.optString("type", null);
        if (type != null) {
            intent.setType(type);
        }
        JSONObject extras = json.optJSONObject("extras");
        if (extras != null) {
            Iterator<String> iter = extras.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                Object val = extras.get(key);
                if (val instanceof Long) {
                    intent.putExtra(key, (Long) val);
                } else if (val instanceof Integer) {
                    intent.putExtra(key, (Integer) val);
                } else if (val instanceof Boolean) {
                    intent.putExtra(key, (Boolean) val);
                } else if (val instanceof Double) {
                    intent.putExtra(key, (Double) val);
                } else if (val instanceof String) {
                    intent.putExtra(key, (String) val);
                } else if (val instanceof JSONArray) {
                    // TODO: improve this, currently assumes that array is string array
                    JSONArray array = (JSONArray) val;
                    int length = array.length();
                    List<String> vals = new ArrayList<>();
                    for (int i = 0; i < length; i++) {
                        vals.add(array.optString(i, ""));
                    }
                    intent.putExtra(key, vals.toArray(new String[0]));
                } else if (val instanceof JSONObject) {
                    // TODO: improve this, currently assumes that object is a <String, String> mapping
                    JSONObject innerObject = (JSONObject) val;
                    Bundle bundle = new Bundle();
                    Iterator<String> innerIter = innerObject.keys();
                    while (innerIter.hasNext()) {
                        String innerKey = innerIter.next();
                        Object innerVal = innerObject.get(innerKey);
                        if (innerVal instanceof String) {
                            bundle.putString(innerKey, (String) innerVal);
                        }
                    }
                    intent.putExtra(key, bundle);
                }
            }
        }
        JSONArray categories = json.optJSONArray("categories");
        if (categories != null) {
            int length = categories.length();
            for (int i = 0; i < length; i++) {
                intent.addCategory(categories.getString(i));
            }
        }
        JSONArray flags = json.optJSONArray("flags");
        if (flags != null) {
            int length = flags.length();
            for (int i = 0; i < length; i++) {
                intent.addFlags(flags.getInt(i));
            }
        }
        return intent;
    }
}