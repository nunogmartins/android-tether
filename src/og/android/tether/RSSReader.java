package og.android.tether;

import android.content.Context;
import android.content.Intent;
import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class RSSReader {
    
    static { 
        System.setProperty("org.xml.sax.driver","org.xmlpull.v1.sax2.Driver");
    }
    
    private final static String TAG = "RSSReader";
    public final static String MESSAGE_JSON_RSS = "og.android.tether.MESSAGE_JSON_RSS";
    public final static String EXTRA_JSON_RSS = "JSONRSS";
    public final static String[] _PARSED_ITEM_ELEMENTS =
        { "title", "link", "pubDate", "description" };    
    public final static String[][] _PARSED_ITEM_DTD_ELEMENTS =
        { { "http://purl.org/dc/elements/1.1/", "creator"} };

    public final String[] PARSED_ITEM_ELEMENTS;
    public final String[][] PARSED_ITEM_DTD_ELEMENTS;
    public final String RSS_URL;
    public final Context mContext;  // pref getApplicationContext()
    
    private JSONArray mRssItems = null;
    private JSONObject mRssItem = null; 
    
    RSSReader(Context context, String RSSUrl) {
        mContext = context;
        RSS_URL = RSSUrl;
        PARSED_ITEM_ELEMENTS = _PARSED_ITEM_ELEMENTS;
        PARSED_ITEM_DTD_ELEMENTS = _PARSED_ITEM_DTD_ELEMENTS;
    }

    RSSReader(Context context, String RSSUrl, String[] parsedItemElements, String[][] parsedItemNSElements) {
        mContext = context;
        RSS_URL = RSSUrl;
        PARSED_ITEM_ELEMENTS = parsedItemElements;
        PARSED_ITEM_DTD_ELEMENTS = parsedItemNSElements;
    }
    
    void readRSS() {
        if(mRssItems != null)
            return;
        new Thread(new Runnable() {
                public void run() {
                    mRssItems = new JSONArray();
                    mRssItem = new JSONObject();
                    parseRSS(httpGetRSS(RSS_URL));
                    mContext.sendBroadcast(new Intent(MESSAGE_JSON_RSS).
                            putExtra(EXTRA_JSON_RSS,  mRssItems.toString()));
                    mRssItems = null;
                }
        }).start();
    }
    
    InputStream httpGetRSS(String url) {
        HttpResponse response = null;
        InputStream content = null;
        try {
            response = new DefaultHttpClient().execute(new HttpGet(RSS_URL));
            content = response.getEntity().getContent();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (response == null) {
            Log.e(TAG, "httpGet failed: no response.");
        }
        else if (response.getStatusLine().getStatusCode() != 200) {
            Log.e(TAG, "httpGet failed: " + response.getStatusLine().getStatusCode());
        } else {
            Log.d(TAG, "Response code: " + response.getStatusLine().getStatusCode());
        }
        
        return content;
    }
        
    void parseRSS(InputStream feed) {        
        XMLReader parser = null;
        try {
            parser = XMLReaderFactory.createXMLReader();
            parser.setContentHandler(getRSSContentHandler());
            parser.parse(new InputSource(feed));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    ContentHandler getRSSContentHandler() {
        RootElement root = new RootElement("rss");
        Element element = root.getChild("channel").getChild("item");
        
        element.setEndElementListener(new EndElementListener() {
            public void end() {
                mRssItems.put(mRssItem);
                mRssItem = new JSONObject();
            }
        });
        for (String el : PARSED_ITEM_ELEMENTS)
            element.getChild(el).setEndTextElementListener(new RSSElementListener(el));
        for (String[] dtdAndEl : PARSED_ITEM_DTD_ELEMENTS)
            element.getChild(dtdAndEl[0], dtdAndEl[1]).setEndTextElementListener(new RSSElementListener(dtdAndEl[1]));
        return root.getContentHandler();
    }

    class RSSElementListener implements EndTextElementListener {
        String element;
        
        RSSElementListener(String element) {
            this.element = element;            
        }
        
        public void end(String body) {
            try {
                mRssItem.put(element, body);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
        
}
