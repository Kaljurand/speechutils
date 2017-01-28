package ee.ioc.phon.android.speechutils.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

import ee.ioc.phon.android.speechutils.Log;

public class HttpUtils {

    public static String getUrl(String url) throws IOException {
        return fetchUrl(url, "GET", null);
    }

    public static String fetchUrl(String myurl, String method, String body) throws IOException {
        byte[] outputInBytes = null;

        if (body != null) {
            outputInBytes = body.getBytes("UTF-8");
        }

        InputStream is = null;

        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(5000 /* milliseconds */);
            conn.setConnectTimeout(5000 /* milliseconds */);
            conn.setRequestMethod(method);
            conn.setDoInput(true);

            if (outputInBytes == null) {
                conn.connect();
            } else {
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(outputInBytes);
                os.close();
            }

            //int response = conn.getResponseCode();
            is = conn.getInputStream();
            return inputStreamToString(is, 1024);
        } finally {
            closeQuietly(is);
        }
    }

    private static void closeQuietly(InputStream is) {
        try {
            if (is != null) {
                is.close();
            }
        } catch (Exception e) {
            Log.i(e.getMessage());
        }
    }

    private static String inputStreamToString(final InputStream is, final int bufferSize) throws IOException {
        final char[] buffer = new char[bufferSize];
        final StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(is, "UTF-8");
        while (true) {
            int rsz = in.read(buffer, 0, buffer.length);
            if (rsz < 0) {
                break;
            }
            out.append(buffer, 0, rsz);
        }
        return out.toString();
    }
}