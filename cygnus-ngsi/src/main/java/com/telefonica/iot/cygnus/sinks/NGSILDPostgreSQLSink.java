package com.telefonica.iot.cygnus.sinks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.telefonica.iot.cygnus.backends.postgresql.PostgreSQLBackendImpl;
import com.telefonica.iot.cygnus.containers.NotifyContextRequestLD;
import com.telefonica.iot.cygnus.containers.NotifyContextRequestLD.ContextElement;
import com.telefonica.iot.cygnus.errors.*;
import com.telefonica.iot.cygnus.interceptors.NGSILDEvent;
import com.telefonica.iot.cygnus.log.CygnusLogger;
import com.telefonica.iot.cygnus.utils.*;
import org.apache.flume.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;

/**
 *
 * @author anmunoz
 */
public class NGSILDPostgreSQLSink extends NGSILDSink {

    private static final String DEFAULT_ROW_ATTR_PERSISTENCE = "row";
    private static final String DEFAULT_PASSWORD = "";
    private static final String DEFAULT_PORT = "5432";
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_USER_NAME = "postgres";
    private static final String DEFAULT_DATABASE = "postgres";
    private static final String DEFAULT_ENABLE_CACHE = "false";
    private static final int DEFAULT_MAX_POOL_SIZE = 3;

    private static final CygnusLogger LOGGER = new CygnusLogger(NGSILDPostgreSQLSink.class);
    private String postgresqlHost;
    private String postgresqlPort;
    private String postgresqlDatabase;
    private String postgresqlUsername;
    private String postgresqlPassword;
    private int maxPoolSize;
    private boolean rowAttrPersistence;
    private PostgreSQLBackendImpl persistenceBackend;
    private boolean enableCache;

    /**
     * Constructor.
     */
    public NGSILDPostgreSQLSink() {
        super();
    } // NGSIPostgreSQLSink

    /**
     * Gets the PostgreSQL host. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL host
     */
    protected String getPostgreSQLHost() {
        return postgresqlHost;
    } // getPostgreSQLHost
    
    /**
     * Gets the PostgreSQL cache. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL cache state
     */
    protected boolean getEnableCache() {
        return enableCache;
    } // getPostgreSQLHost

    /**
     * Gets the PostgreSQL port. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL port
     */
    protected String getPostgreSQLPort() {
        return postgresqlPort;
    } // getPostgreSQLPort

    /**
     * Gets the PostgreSQL database. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL database
     */
    protected String getPostgreSQLDatabase() {
        return postgresqlDatabase;
    } // getPostgreSQLDatabase

    /**
     * Gets the PostgreSQL username. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL username
     */
    protected String getPostgreSQLUsername() {
        return postgresqlUsername;
    } // getPostgreSQLUsername

    /**
     * Gets the PostgreSQL password. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL password
     */
    protected String getPostgreSQLPassword() {
        return postgresqlPassword;
    } // getPostgreSQLPassword

    /**
     * Returns if the attribute persistence is row-based. It is protected due to it is only required for testing
     * purposes.
     * @return True if the attribute persistence is row-based, false otherwise
     */
    protected boolean getRowAttrPersistence() {
        return rowAttrPersistence;
    } // getRowAttrPersistence

    /**
     * Returns the persistence backend. It is protected due to it is only required for testing purposes.
     * @return The persistence backend
     */
    protected PostgreSQLBackendImpl getPersistenceBackend() {
        return persistenceBackend;
    } // getPersistenceBackend

    /**
     * Sets the persistence backend. It is protected due to it is only required for testing purposes.
     * @param persistenceBackend
     */
    protected void setPersistenceBackend(PostgreSQLBackendImpl persistenceBackend) {
        this.persistenceBackend = persistenceBackend;
    } // setPersistenceBackend

    @Override
    public void configure(Context context) {
        // Read NGSISink general configuration
        super.configure(context);

        // Impose enable lower case, since PostgreSQL only accepts lower case
        enableLowercase = true;
        
        postgresqlHost = context.getString("postgresql_host", DEFAULT_HOST);
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_host=" + postgresqlHost + ")");
        postgresqlPort = context.getString("postgresql_port", DEFAULT_PORT);
        int intPort = Integer.parseInt(postgresqlPort);

        if ((intPort <= 0) || (intPort > 65535)) {
            invalidConfiguration = true;
            LOGGER.debug("[" + this.getName() + "] Invalid configuration (postgresql_port=" + postgresqlPort + ")"
                    + " -- Must be between 0 and 65535");
        } else {
            LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_port=" + postgresqlPort + ")");
        }  // if else

        postgresqlDatabase = context.getString("postgresql_database", DEFAULT_DATABASE);
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_database=" + postgresqlDatabase + ")");
        postgresqlUsername = context.getString("postgresql_username", DEFAULT_USER_NAME);
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_username=" + postgresqlUsername + ")");
        // FIXME: postgresqlPassword should be read as a SHA1 and decoded here
        postgresqlPassword = context.getString("postgresql_password", DEFAULT_PASSWORD);
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_password=" + postgresqlPassword + ")");

        maxPoolSize = context.getInteger("postgresql_maxPoolSize", DEFAULT_MAX_POOL_SIZE);
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_maxPoolSize=" + maxPoolSize + ")");

        rowAttrPersistence = context.getString("attr_persistence", DEFAULT_ROW_ATTR_PERSISTENCE).equals("row");
        String persistence = context.getString("attr_persistence", DEFAULT_ROW_ATTR_PERSISTENCE);


        if (persistence.equals("row") || persistence.equals("column")) {
            LOGGER.debug("[" + this.getName() + "] Reading configuration (attr_persistence="
                + persistence + ")");
        } else {
            invalidConfiguration = true;
            LOGGER.debug("[" + this.getName() + "] Invalid configuration (attr_persistence="
                + persistence + ") -- Must be 'row' or 'column'");
        }  // if else
                
        String enableCacheStr = context.getString("backend.enable_cache", DEFAULT_ENABLE_CACHE);
        
        if (enableCacheStr.equals("true") || enableCacheStr.equals("false")) {
            enableCache = Boolean.valueOf(enableCacheStr);
            LOGGER.debug("[" + this.getName() + "] Reading configuration (backend.enable_cache=" + enableCache + ")");
        }  else {
            invalidConfiguration = true;
            LOGGER.debug("[" + this.getName() + "] Invalid configuration (backend.enable_cache="
                + enableCache + ") -- Must be 'true' or 'false'");
        }  // if else
        
    } // configure

    @Override
    public void start() {
        try {
            persistenceBackend = new PostgreSQLBackendImpl(postgresqlHost, postgresqlPort, postgresqlDatabase, postgresqlUsername, postgresqlPassword, maxPoolSize);
        } catch (Exception e) {
            LOGGER.error("Error while creating the PostgreSQL persistence backend. Details="
                    + e.getMessage());
        } // try catch

        super.start();
        LOGGER.info("[" + this.getName() + "] Startup completed");
    } // start

    @Override
    public void persistBatch(NGSILDBatch batch)
        throws CygnusBadConfiguration, CygnusPersistenceError, CygnusRuntimeError, CygnusBadContextData {
        if (batch == null) {
            LOGGER.debug("[" + this.getName() + "] Null batch, nothing to do");
            return;
        } // if

        // Iterate on the destinations
        batch.startIterator();
        
        while (batch.hasNext()) {
            String destination = batch.getNextDestination();
            LOGGER.debug("[" + this.getName() + "] Processing sub-batch regarding the "
                    + destination + " destination");

            // get the sub-batch for this destination
            ArrayList<NGSILDEvent> events = batch.getNextEvents();

            // get an aggregator for this destination and initialize it
            PostgreSQLAggregator aggregator = getAggregator(rowAttrPersistence);
            aggregator.initialize(events.get(0));

            for (NGSILDEvent event : events) {
                aggregator.aggregate(event);
            } // for

            // persist the fieldValues
            persistAggregation(aggregator);
            batch.setNextPersisted(true);
        } // for
    } // persistBatch
    
    @Override
    public void capRecords(NGSILDBatch batch, long maxRecords) throws CygnusCappingError {
    } // capRecords

    @Override
    public void expirateRecords(long expirationTime) throws CygnusExpiratingError {
    } // expirateRecords

    /**
     * Class for aggregating fieldValues.
     */
    private abstract class PostgreSQLAggregator {

        // string containing the data fieldValues
        protected String aggregation;

        protected String service;
        protected String servicePathForData;
        protected String servicePathForNaming;
        protected String entityForNaming;
        protected String attributeForNaming;
        protected String schemaName;
        protected String tableName;
        protected String typedFieldNames;
        protected String fieldNames;

        public PostgreSQLAggregator() {
            aggregation = "";
        } // PostgreSQLAggregator

        public String getAggregation() {
            return aggregation;
        } // getAggregation

        public String getSchemaName(boolean enableLowercase) {
            if (enableLowercase) {
                return schemaName.toLowerCase();
            } else {
                return schemaName;
            } // if else
        } // getDbName

        public String getTableName(boolean enableLowercase) {
            if (enableLowercase) {
                return tableName.toLowerCase();
            } else {
                return tableName;
            } // if else
        } // getTableName

        public String getTypedFieldNames() {
            return typedFieldNames;
        } // getTypedFieldNames

        public String getFieldNames() {
            return fieldNames;
        } // getFieldNames

        public void initialize(NGSILDEvent event) throws CygnusBadConfiguration {
            service = event.getServiceForNaming();
            servicePathForData = event.getServicePathForData();
            servicePathForNaming = event.getServicePathForNaming(enableGrouping);
            entityForNaming = event.getEntityForNaming(enableGrouping, enableEncoding);
            attributeForNaming = event.getAttributeForNaming();
            schemaName = buildSchemaName(service);
            tableName = buildTableName(entityForNaming, attributeForNaming);
        } // initialize

        public abstract void aggregate(NGSILDEvent cygnusEvent);

    } // PostgreSQLAggregator

    /**
     * Class for aggregating batches in row mode.
     */
    private class RowAggregator extends PostgreSQLAggregator {

        @Override
        public void initialize(NGSILDEvent cygnusEvent) throws CygnusBadConfiguration {
            super.initialize(cygnusEvent);
            typedFieldNames = "("
                    + NGSIConstants.RECV_TIME_TS + " bigint,"
                    + NGSIConstants.RECV_TIME + " text,"
                    + NGSIConstants.FIWARE_SERVICE_PATH + " text,"
                    + NGSIConstants.ENTITY_ID + " text,"
                    + NGSIConstants.ENTITY_TYPE + " text,"
                    + NGSIConstants.ATTR_NAME + " text,"
                    + NGSIConstants.ATTR_TYPE + " text,"
                    + NGSIConstants.ATTR_VALUE + " text,"
                    + NGSIConstants.ATTR_MD + " text"
                    + ")";
            fieldNames = "("
                    + NGSIConstants.RECV_TIME_TS + ","
                    + NGSIConstants.RECV_TIME + ","
                    + NGSIConstants.FIWARE_SERVICE_PATH + ","
                    + NGSIConstants.ENTITY_ID + ","
                    + NGSIConstants.ENTITY_TYPE + ","
                    + NGSIConstants.ATTR_NAME + ","
                    + NGSIConstants.ATTR_TYPE + ","
                    + NGSIConstants.ATTR_VALUE + ","
                    + NGSIConstants.ATTR_MD
                    + ")";
        } // initialize

        @Override
        public void aggregate(NGSILDEvent event) {
            // get the getRecvTimeTs headers
            long recvTimeTs = event.getRecvTimeTs();
            String recvTime = CommonUtils.getHumanReadable(recvTimeTs, true);

            // get the getRecvTimeTs body
            ContextElement contextElement = event.getContextElement();
            String entityId = contextElement.getId();
            String entityType = contextElement.getType();
            LOGGER.debug("[" + getName() + "] Processing context element (id=" + entityId + ", type="
                    + entityType + ")");

            // iterate on all this context element attributes, if there are attributes
            /*ArrayList<ContextAttribute> contextAttributes = contextElement.getAttributes();

            if (contextAttributes == null || contextAttributes.isEmpty()) {
                LOGGER.warn("No attributes within the notified entity, nothing is done (id=" + entityId
                        + ", type=" + entityType + ")");
                return;
            } // if

            for (ContextAttribute contextAttribute : contextAttributes) {
                String attrName = contextAttribute.getName();
                String attrType = contextAttribute.getType();
                String attrValue = contextAttribute.getContextValue(false);
                String attrMetadata = contextAttribute.getContextMetadata();
                LOGGER.debug("[" + getName() + "] Processing context attribute (name=" + attrName + ", type="
                        + attrType + ")");

                // create a column and aggregate it
                String row = "('"
                    + recvTimeTs + "','"
                    + recvTime + "','"
                    + servicePathForData + "','"
                    + entityId + "','"
                    + entityType + "','"
                    + attrName + "','"
                    + attrType + "','"
                    + attrValue + "','"
                    + attrMetadata
                    + "')";

                if (aggregation.isEmpty()) {
                    aggregation += row;
                } else {
                    aggregation += "," + row;
                } // if else
            } // for*/
        } // aggregate

    } // RowAggregator

    /**
     * Class for aggregating batches in column mode.
     */
    private class ColumnAggregator extends PostgreSQLAggregator {

        @Override
        public void initialize(NGSILDEvent cygnusEvent) throws CygnusBadConfiguration {
            super.initialize(cygnusEvent);

            // particulat initialization
            typedFieldNames = "(" + NGSIConstants.RECV_TIME + " text,"
                    + NGSIConstants.ENTITY_ID + " text,"
                    + NGSIConstants.ENTITY_TYPE + " text";
            fieldNames = "(" + NGSIConstants.RECV_TIME + ","
                    + NGSIConstants.ENTITY_ID + ","
                    + NGSIConstants.ENTITY_TYPE;

            // iterate on all this context element attributes, if there are attributes
            Map<String, Object> contextAttributes = cygnusEvent.getContextElement().getAttributes();

            if (contextAttributes == null || contextAttributes.isEmpty()) {
                return;
            } // if

            for (Map.Entry<String, Object> entry : contextAttributes.entrySet()) {
                String x = entry.getKey();
                String attrName =x;
                String subAttrName="";
                typedFieldNames += "," + attrName + " text";
                fieldNames += "," + attrName;
                if (isValid(entry.getValue().toString())) {
                    JsonObject y = (JsonObject) entry.getValue();
                    for (Map.Entry<String, JsonElement> entry2 : y.entrySet()) {
                        String x2 = entry2.getKey();
                        Object y2 = entry2.getValue();
                        if (!"type".contentEquals(x2) && !"value".contentEquals(x2)
                                && !"object".contentEquals(x2)) {
                            subAttrName = x2;
                            typedFieldNames += "," + attrName + "_" + subAttrName + " text";
                            fieldNames += "," + attrName + "_" + subAttrName;
                        }
                    }
                }
            }
            typedFieldNames += ")";
            fieldNames += ")";
        } // initialize

        @Override
        public void aggregate(NGSILDEvent cygnusEvent) {
            // get the getRecvTimeTs headers
            long recvTimeTs = cygnusEvent.getRecvTimeTs();
            String recvTime = CommonUtils.getHumanReadable(recvTimeTs, true);

            // get the getRecvTimeTs body
            ContextElement contextElement = cygnusEvent.getContextElement();
            String entityId = contextElement.getId();
            String entityType = contextElement.getType();
            LOGGER.debug("[" + getName() + "] Processing context element (id=" + entityId + ", type="
                    + entityType + ")");

            // iterate on all this context element attributes, if there are attributes
            Map<String, Object> contextAttributes = contextElement.getAttributes();

            if (contextAttributes == null || contextAttributes.isEmpty()) {
                LOGGER.warn("No attributes within the notified entity, nothing is done (id=" + entityId
                        + ", type=" + entityType + ")");
                return;
            } // if

            String column = "('" + recvTime + "','" + entityId + "','" + entityType + "'";
            for (Map.Entry<String, Object> entry : contextAttributes.entrySet()) {
                String x = entry.getKey();
                String attrValue ="";
                String attrType ="";
                String subAttrName="";
                String subAttrType="";
                if (isValid(entry.getValue().toString())){
                    JsonObject y = (JsonObject) entry.getValue();
                    attrType = y.get("type").getAsString();
                    if ("Relationship".contentEquals(attrType)){
                        attrValue ="'"+y.get("object").getAsString()+"'";
                    }else if ("Property".contentEquals(attrType)){
                         attrValue =(y.get("value").isJsonObject()) ? "'"+y.get("value").toString()+"'": "'"+y.get("value").getAsString()+"'";
                    }else if ("GeoProperty".contentEquals(attrType)){
                        attrValue ="'"+y.get("value").toString()+"'";
                    }
                    column += "," +attrValue;

                    for (Map.Entry<String, JsonElement> entry2 : y.entrySet()) {
                        String x2 = entry2.getKey();
                        Object y2 = entry2.getValue();
                        if ("observedAt".contentEquals(x2)){
                            column += "," + "'"+entry2.getValue().getAsString()+"'";
                        }
                        if (!"observedAt".contentEquals(x2) && !"type".contentEquals(x2) && !"value".contentEquals(x2) && !"object".contentEquals(x2)) {
                            if (entry2.getValue().isJsonObject()){
                                JsonObject subAttrJson = entry2.getValue().getAsJsonObject();
                                subAttrType = subAttrJson.get("type").getAsString();
                                if ("Relationship".contentEquals(subAttrType)){
                                    subAttrName ="'"+subAttrJson.get("object").getAsString()+"'";
                                }else if ("Property".contentEquals(subAttrType)){
                                    subAttrName ="'"+subAttrJson.get("value").getAsString()+"'";
                                }else if ("GeoProperty".contentEquals(subAttrType)){
                                    subAttrName ="'"+subAttrJson.get("value").toString()+"'";
                                }
                            }
                            column += "," + subAttrName ;
                        }
                    }
                }else {
                    attrValue= entry.getValue().toString();
                    column += ",'"+attrValue+"'";
                }
            }
             // for

            // now, aggregate the column
            if (aggregation.isEmpty()) {
                aggregation += column + ")";
            } else {
                aggregation += "," + column + ")";
            } // if else
        } // aggregate

    } // ColumnAggregator

    private PostgreSQLAggregator getAggregator(boolean rowAttrPersistence) {
        if (rowAttrPersistence) {
            return new RowAggregator();
        } else {
            return new ColumnAggregator();
        } // if else
    } // getAggregator

    private void persistAggregation(PostgreSQLAggregator aggregator) throws CygnusPersistenceError, CygnusRuntimeError, CygnusBadContextData {
        String typedFieldNames = aggregator.getTypedFieldNames();
        String fieldNames = aggregator.getFieldNames();
        String fieldValues = aggregator.getAggregation();
        String schemaName = aggregator.getSchemaName(enableLowercase);
        String tableName = aggregator.getTableName(enableLowercase);

        LOGGER.info("[" + this.getName() + "] Persisting data at NGSIPostgreSQLSink. Schema ("
                + schemaName + "), Table (" + tableName + "), Fields (" + fieldNames + "), Values ("
                + fieldValues + ")");
        
        try {
            if (aggregator instanceof RowAggregator) {
                persistenceBackend.createSchema(schemaName);
                persistenceBackend.createTable(schemaName, tableName, typedFieldNames);
            } else if (aggregator instanceof ColumnAggregator){
                persistenceBackend.createSchema(schemaName);
                persistenceBackend.createTable(schemaName, tableName, typedFieldNames);
            }// if
            // creating the database and the table has only sense if working in row mode, in column node
            // everything must be provisioned in advance

            persistenceBackend.insertContextData(schemaName, tableName, fieldNames, fieldValues);
        } catch (Exception e) {
            throw new CygnusPersistenceError("-, " + e.getMessage());
        } // try catch
    } // persistAggregation
    
    /**
     * Creates a PostgreSQL DB name given the FIWARE service.
     * @param service
     * @return The PostgreSQL DB name
     * @throws CygnusBadConfiguration
     */
    public String buildSchemaName(String service) throws CygnusBadConfiguration {
        String name;
        
        if (enableEncoding) {
            name = NGSICharsets.encodePostgreSQL(service);
        } else {
            name = NGSIUtils.encode(service, false, true);
        } // if else

        if (name.length() > NGSIConstants.POSTGRESQL_MAX_NAME_LEN) {
            throw new CygnusBadConfiguration("Building schema name '" + name
                    + "' and its length is greater than " + NGSIConstants.POSTGRESQL_MAX_NAME_LEN);
        } // if

        return name;
    } // buildSchemaName

    /**
     * Creates a PostgreSQL table name given the FIWARE service path, the entity and the attribute.
     * @param entity
     * @param entityType
     * @return The PostgreSQL table name
     * @throws CygnusBadConfiguration
     */
    public String buildTableName(String entity, String entityType) throws CygnusBadConfiguration {
        String name;

        if (enableEncoding) {
            switch(dataModel) {

                case DMBYENTITY:
                    name = NGSICharsets.encodePostgreSQL(entity);
                    break;
                case DMBYENTITYTYPE:
                    name = NGSICharsets.encodePostgreSQL(entityType);
                    break;
                default:
                    throw new CygnusBadConfiguration("Unknown data model '" + dataModel.toString()
                            + "'. Please, use dm-by-entity or dm-by-entity-type");
            } // switch
        } else {
            switch(dataModel) {

                case DMBYENTITY:
                    name = NGSIUtils.encodePostgreSQL(entity);
                    break;
                case DMBYENTITYTYPE:
                    name = (NGSIUtils.encodePostgreSQL(entityType));

                    break;
                default:
                    throw new CygnusBadConfiguration("Unknown data model '" + dataModel.toString()
                            + "'. Please, use DMBYENTITY or DMBYENTITYTYPE");
            } // switch
        } // if else

        if (name.length() > NGSIConstants.POSTGRESQL_MAX_NAME_LEN) {
            throw new CygnusBadConfiguration("Building table name '" + name
                    + "' and its length is greater than " + NGSIConstants.POSTGRESQL_MAX_NAME_LEN);
        } // if

        return name;
    } // buildTableName

    public boolean isValid(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

} // NGSIPostgreSQLSink
