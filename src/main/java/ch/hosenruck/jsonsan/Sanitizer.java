package ch.hosenruck.jsonsan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.vavr.Tuple2;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * Sanitizes a given JSON schema.
 */
public class Sanitizer {

    private static final String PN_PROPERTIES = "properties";
    private static final String PN_DEFINITIONS = "definitions";
    private static final String PN_ITEMS = "items";
    private static final String PN_REF = "$ref";

    private Tuple2<Path, JsonObject> schema;

    /**
     * Creates a new instance.
     *
     * @param schema a tuple containing the path to the schema and the root node of the schema.
     */
    public Sanitizer(Tuple2<Path, JsonObject> schema) {
        this.schema = schema;
    }

    /**
     * Moves deeply nested object properties to the {@link #PN_DEFINITIONS} node and references the moved property with
     * a schema pointer.
     *
     * @return the root node of the modified schema or {@code null} if no valid input schema was given.
     */
    public JsonObject unnestInlinedObjects() {
        if (schema._2 == null) {
            System.err.printf("No JSON found at path '%s'", schema._1);
            return null;
        }

        final JsonObject rootNode = schema._2;
        final JsonObject definitionsNode = getOrCreateDefinitionsNode(rootNode);

        // Move root properties to definitions
        replaceWithReference(definitionsNode, rootNode);

        // Check/Move externalized objects in the definitions node
        List<JsonElement> nodesToExternalize = null;
        while (!(nodesToExternalize = getProcessableNodes(definitionsNode)).isEmpty()) {
            final JsonElement currentNode = nodesToExternalize.remove(0);
            replaceWithReference(
                    definitionsNode,
                    currentNode
                    );
        }
        return rootNode;
    }

    public Path getOriginalSchemaLocation() {
        return schema._1;
    }

    private JsonObject getOrCreateDefinitionsNode(JsonObject rootNode) {
        if (rootNode == null) {
            return null;
        }
        if (!rootNode.has(PN_DEFINITIONS)) {
            System.err.println("root node has no 'definitions' node, creating an empty 'definitions' node");
            rootNode.add(PN_DEFINITIONS, new JsonObject());
        }
        return rootNode.getAsJsonObject(PN_DEFINITIONS);
    }

    private List<JsonElement> getProcessableNodes(JsonObject definitions) {
        return definitions.entrySet()
                .stream()
                .filter(entry -> entry.getValue() instanceof JsonObject)
                .filter(entry -> !((JsonObject) entry.getValue()).has(PN_REF))
                .filter(entry -> ((JsonObject) entry.getValue()).has(PN_PROPERTIES))
                .filter(entry -> hasAtLeastOnePropertyToBeExternalized((JsonObject) entry.getValue()))
                .map(Map.Entry::getValue)
                .collect(toList());
    }

    private boolean hasAtLeastOnePropertyToBeExternalized(JsonObject node) {
        final JsonObject propertiesNode = (JsonObject) node.get(PN_PROPERTIES);

        if (propertiesNode == null) {
            return false;
        }
        for (Map.Entry<String, JsonElement> property: propertiesNode.entrySet()) {
                if ((property.getValue() instanceof JsonObject)) {
                    final JsonElement propertyValue = property.getValue();

                    // Object Property
                    if (((JsonObject) propertyValue).has(PN_PROPERTIES)) {
                        return true;
                    }
                    // Array Property
                    if (((JsonObject) propertyValue).has(PN_ITEMS)) {
                        final JsonObject items = (JsonObject) ((JsonObject) propertyValue).get(PN_ITEMS);
                        if (items.has(PN_PROPERTIES)) {
                            return true;
                        }
                    }
                }
        }
        return false;
    }

    private void replaceWithReference(JsonObject definitions, JsonElement root) {
        if (!(root instanceof JsonObject)) {
            return;
        }
        final JsonObject currentNode = (JsonObject) root;
        if (!currentNode.has(PN_PROPERTIES)) {
            return;
        }
        final JsonObject properties = currentNode.getAsJsonObject(PN_PROPERTIES);

        // Object properties
        properties.entrySet()
                .stream()
                .peek(entry -> System.out.printf("Checking key '%s'\n", entry.getKey()))
                .filter(entry -> entry.getValue() instanceof JsonObject)
                .filter(entry -> !((JsonObject) entry.getValue()).has(PN_REF))
                .filter(entry -> ((JsonObject) entry.getValue()).has(PN_PROPERTIES))
                .forEach(entry -> moveToDefinitions(entry, definitions));

        // Array item properties
        properties.entrySet()
                .stream()
                .peek(entry -> System.out.printf("Checking key '%s'\n", entry.getKey()))
                .filter(entry -> entry.getValue() instanceof JsonObject)
                .filter(entry -> !((JsonObject) entry.getValue()).has("$ref"))
                .filter(entry -> ((JsonObject) entry.getValue()).has(PN_ITEMS))
                .forEach(entry -> moveArrayItemToDefinitions(entry, definitions));
    }

    /**
     * Moves the given {@code entry} node in the global 'definitions' node and replaces the entry node with a reference
     * to the externalized node.
     *
     * @param entry the JSON node to be externalized.
     * @param definitions the definitions object
     */
    private void moveToDefinitions(Map.Entry<String, JsonElement> entry, JsonObject definitions) {
        // Add to definitions
        final JsonObject movable = (JsonObject) entry.getValue();

        definitions.add(entry.getKey(), movable.deepCopy());

        // Replace
        movable.entrySet().clear();
        movable.addProperty(PN_REF, "#/definitions/" + entry.getKey());
        System.out.println("Externalized key: " + entry.getKey());
    }

    private void moveArrayItemToDefinitions(Map.Entry<String, JsonElement> entry, JsonObject definitions) {
        // Add to definitions
        final JsonObject movable = (JsonObject) entry.getValue();

        definitions.add(entry.getKey() + "_item", movable.get(PN_ITEMS).deepCopy());

        // Replace
        final JsonObject itemObject = new JsonObject();
        itemObject.addProperty(PN_REF, "#/definitions/" + entry.getKey() + "_item");
        movable.add(PN_ITEMS, itemObject);
        System.out.println("Externalized key: " + entry.getKey());
    }
}
