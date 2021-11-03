package ca.utoronto.utm.mcs;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.mongodb.BasicDBObject;
import com.mongodb.client.*;
import org.bson.BsonDocument;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.print.Doc;
import javax.inject.Inject;

public class PostHandler implements HttpHandler {
    private MongoClient db;

    @Inject
    public PostHandler(MongoClient db) {
        this.db = db;
    }

    public void handle(HttpExchange r) throws IOException {
        try {
            if (r.getRequestMethod().equals("PUT")) {
                handlePut(r);
            } else if (r.getRequestMethod().equals("GET")) {
                handleGet(r);
            } else if (r.getRequestMethod().equals("DELETE")) {
                handleDelete(r);
            } else {
                throw new MethodNotAllowedException("Invalid Request");
            }
        } catch (MethodNotAllowedException e1) {
            r.sendResponseHeaders(405, -1);
        } catch (BadRequestException e2) {
            r.sendResponseHeaders(400, -1);
        } catch (NotFoundException e3) {
            r.sendResponseHeaders(404, -1);
        } catch (Exception e4) {
            r.sendResponseHeaders(500, -1);
        }
    }

    private void handlePut(HttpExchange r) throws Exception, BadRequestException {
        String body = Utils.convert(r.getRequestBody());
        JSONObject deserialized = new JSONObject(body);

        String title = "";
        String author = "";
        String content = "";
        JSONArray jsonArray = new JSONArray();
        if (deserialized.has("title") && deserialized.has("author") &&
                deserialized.has("content") && deserialized.has("tags")) {
            title = deserialized.getString("title");
            author = deserialized.getString("author");
            content = deserialized.getString("content");
            jsonArray = deserialized.getJSONArray("tags");
        } else {
            throw new BadRequestException("Missing information in JSON");
        }

        //convert jsonarray to arraylist
        List<String> tags = new ArrayList<>();
        for (int i=0; i<jsonArray.length(); i++) {
            tags.add(jsonArray.getString(i));
        }

        Document post = new Document();
        post.put("title", title);
        post.put("author", author);
        post.put("content", content);
        post.put("tags", tags);
        MongoDatabase database = db.getDatabase("csc301a2");
        createCollection("posts");
        database.getCollection("posts").insertOne(post);

        //get the id of the inserted post
        String id = post.getObjectId("_id").toString();

        //return response with id
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("_id", id);
        String response = jsonObject.toString();
        r.sendResponseHeaders(200, response.length());
        OutputStream os = r.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private void handleGet(HttpExchange r) throws Exception, BadRequestException, IOException {
        String body = Utils.convert(r.getRequestBody());
        JSONObject deserialized = new JSONObject(body);

        String title = "";
        String id = "";
        boolean filterByTitle = false;
        boolean filterById = false;
        String response = "";

        // Check the title and id fields
        if (deserialized.has("title")) {
            filterByTitle = true;
            title = deserialized.getString("title");
        }
        if (deserialized.has("_id")) {
            filterById = true;
            id = deserialized.getString("_id");
            if(!ObjectId.isValid(id)) {
                throw new BadRequestException("Id is not a valid objectid (hexadecimal string)");
            }
        }
        if((filterById==false)&&(filterByTitle==false))
            throw new BadRequestException("Missing information in JSON");

        // Do our queries on the posts
        if((filterById && filterByTitle) || (filterById)) {
            response = queryById(id);

            // If post does not exist
            if (response.equals("[]")) {
                throw new NotFoundException("Post does not exist, when querying by id");
            }
        }
        else if (filterByTitle) {
            response = queryByTitle(title);

            // If post does not exist
            if (response.equals("[]")) {
                throw new NotFoundException("Post does not exist, when querying by title");
            }
        }

        r.sendResponseHeaders(200, response.length());
        OutputStream os = r.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private void handleDelete(HttpExchange r) throws Exception, BadRequestException, NotFoundException {
        String body = Utils.convert(r.getRequestBody());
        JSONObject deserialized = new JSONObject(body);

        String id = "";

        if (deserialized.has("_id")){
            id = deserialized.getString("_id");
            if(!ObjectId.isValid(id)) {
                throw new BadRequestException("Id is not a valid objectid (hexadecimal string)");
            }
        } else {
            throw new BadRequestException("Missing information in JSON");
        }

        MongoDatabase database = db.getDatabase("csc301a2");
        MongoCollection collection = database.getCollection("posts");

        BasicDBObject query = new BasicDBObject();
        query.put("_id", new ObjectId(id));

        if(collection.findOneAndDelete(query) != null) {
            r.sendResponseHeaders(200, -1);
        } else {
            throw new NotFoundException("Post not found");
        }
    }

    private void createCollection(String name) {
        MongoDatabase database = db.getDatabase("csc301a2");
        List<String> collections = database.listCollectionNames().into(new ArrayList<>());
        boolean create = true;
        for (String collection: collections) {
            if (collection.equals(name)) {
                create = false;
            }
        }
        if (create) {
            database.createCollection(name);
        }
    }

    private String queryById(String id) throws JSONException {
        MongoDatabase database = db.getDatabase("csc301a2");
        MongoCollection collection = database.getCollection("posts");

        BasicDBObject query = new BasicDBObject();
        query.put("_id", new ObjectId(id));
        MongoIterable<Document> posts = collection.find(query);

        JSONArray jsonArray = new JSONArray();
        Iterator iterator = posts.iterator();
        while(iterator.hasNext()) {
            Document post = (Document) iterator.next();
            JSONObject jsonObject = new JSONObject(post.toJson());
            jsonArray.put(jsonObject);
        }

        return jsonArray.toString();
    }

    private String queryByTitle(String title) throws JSONException {
        MongoDatabase database = db.getDatabase("csc301a2");
        MongoCollection collection = database.getCollection("posts");

        MongoIterable<Document> posts = collection.find().sort(new BasicDBObject("title", 1));

        JSONArray jsonArray = new JSONArray();
        Iterator iterator = posts.iterator();
        while(iterator.hasNext()) {
            Document post = (Document) iterator.next();

            if (post.get("title").toString().contains(title)) {
                JSONObject jsonObject = new JSONObject(post.toJson());
                jsonArray.put(jsonObject);
            }
        }

        return jsonArray.toString();
    }
}
