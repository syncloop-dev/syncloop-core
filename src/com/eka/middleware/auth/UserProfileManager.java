package com.eka.middleware.auth;

import com.eka.middleware.adapter.SQL;
import com.eka.middleware.auth.db.repository.TenantRepository;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.auth.db.entity.Groups;
import com.eka.middleware.auth.db.entity.Users;
import com.eka.middleware.auth.db.repository.GroupsRepository;
import com.eka.middleware.auth.db.repository.UsersRepository;
import com.eka.middleware.template.SystemException;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ldaptive.auth.User;
import org.pac4j.core.profile.UserProfile;

import java.io.File;
import java.security.Principal;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.eka.middleware.auth.db.repository.GroupsRepository.*;
import static com.eka.middleware.auth.db.repository.TenantRepository.*;
import static com.eka.middleware.auth.db.repository.TenantRepository.getTenantIdByName;
import static com.eka.middleware.auth.db.repository.UsersRepository.doesMappingExist;
import static com.eka.middleware.auth.db.repository.UsersRepository.getUserById;

public class UserProfileManager implements IdentityManager {
    public static final Map<String, Object> usersMap = new ConcurrentHashMap<String, Object>();
    private static final Set<String> tenants = new HashSet();
    private static final Set<String> groups = new HashSet();
    private static UserProfileManager upm = null;
    public static Logger LOGGER = LogManager.getLogger(UserProfileManager.class);

    @Deprecated
    public static final Map<String, Object> getUsers() throws SystemException {
        return UsersRepository.getUsers();
    }

    @Deprecated
    public static List<String> getGroups() throws SystemException {
        return GroupsRepository.getAllGroups();
    }

    //1- getGroups for tenant
    public static List<String> getGroupsForTenant(DataPipeline dataPipeline) throws SystemException {
       String tenant= dataPipeline.rp.getTenant().getName();
        return GroupsRepository.getGroupsForTenant(getTenantIdByName(tenant));
    }

    public static List<String> getTenants() {
        return TenantRepository.getAllTenants();
    }

    public static int newTenant(String name) throws Exception {
        return TenantRepository.create(name);
    }

    public static void newGroup(String name) throws Exception {
        Groups group = new Groups(name, 1);
        GroupsRepository.addGroup(group);
    }

    //2- new Group for tenant
   /* public static void createGroupForTenant(String groupName, DataPipeline dataPipeline) throws Exception {
        String tenantName = dataPipeline.rp.getTenant().getName();
        int tenantId = getTenantIdByName(tenantName);
        if (tenantId != -1) {
            Timestamp createdDate = new Timestamp(System.currentTimeMillis());
            Groups group = new Groups(groupName, tenantId,createdDate,createdDate,0);
            GroupsRepository.addGroup(group);
        } else {
            throw new Exception("Tenant not found: " + tenantName);
        }
    }*/

    //2- new Group for tenant with support of modifying existing group
    public static void createGroupForTenant(String groupName, DataPipeline dataPipeline) throws Exception {
        String tenantName = dataPipeline.rp.getTenant().getName();
        int tenantId = getTenantIdByName(tenantName);
        if (tenantId != -1) {
            if (containsSpecialCharacters(groupName)) {
                throw new Exception("Group name can not contains special characters: " + groupName);
            }
            Timestamp createdDate = new Timestamp(System.currentTimeMillis());
            // Check if a group with the same name exists and is deleted
            Groups existingGroup = GroupsRepository.getGroupByNameAndTenant(groupName, tenantId);
            if (existingGroup != null) {
                if (existingGroup.getDeleted() == 1) {
                    existingGroup.setDeleted(0);
                    existingGroup.setModified_date(createdDate);
                    GroupsRepository.updateGroup(existingGroup);
                }
            }else {
                    Groups group = new Groups(groupName, tenantId, createdDate, createdDate, 0);
                    GroupsRepository.addGroup(group);
                }

        }else {
            throw new Exception("Tenant not found: " + tenantName);
        }
    }

    private static boolean containsSpecialCharacters(String input) {
        String regex = "[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]+";
        return input.matches(".*" + regex + ".*");
    }

    public static void removeGroup(String name) throws Exception {
        GroupsRepository.deleteGroup(name);
    }

    //3- remove group for tenant
    public static void removeGroupForTenant(String groupName,DataPipeline dataPipeline) throws Exception {
        String tenantName = dataPipeline.rp.getTenant().getName();
        int tenantId = getTenantIdByName(tenantName);
        deleteGroupForTenant(groupName, tenantId);
    }

    public static boolean isUserExist(String user) throws SystemException {
        try (Connection connection = SQL.getProfileConnection(false);) {
            return UsersRepository.isUserExist(connection, user);
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static Users addUser(AuthAccount account, String passwordStr) throws SystemException {
        try {
            if (isUserExist(account.getUserId())) {
                throw new Exception("User already exists: " + account.getUserId());
            }
            Map<String, Object> user = new HashMap();
            user.put("profile", account.getAuthProfile());

            byte[] password = passwordStr.getBytes();

            return UsersRepository.addUser(createUserFromAccount(account, password));

        } catch (Exception e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    //add user for tenant
    public static void addUserForTenant(AuthAccount account,DataPipeline dataPipeline) throws SystemException {
        try {
            if (isUserExist(account.getUserId())) {
                throw new Exception("User already exists: " + account.getUserId());
            }
            Map<String, Object> user = new HashMap();
            user.put("profile", account.getAuthProfile());
            byte[] password = null;
            if (account.getUserId().equals("admin")) {
                password = "admin".getBytes();
            } else {
                password = "".getBytes();
            }
            UsersRepository.addUser(createUserFromAccount(account, password,dataPipeline));
        } catch (Exception e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    private static Users createUserFromAccount(AuthAccount account, byte[] password) throws SystemException {
        Map<String, Object> profile = account.getAuthProfile();
        String name = profile.get("name") != null ? profile.get("name").toString() : "";
        String email = profile.get("email") != null ? profile.get("email").toString() : "";
        List<String> groupName = (List<String>) profile.get("groups");
        List<Groups> groups = groupName.stream()
                .map(Groups::new)
                .collect(Collectors.toList());
        String tenant = profile.get("tenant").toString();
        String userId = account.getUserId();
        String passHash = null;
        if (null != password) {
            passHash = "[#]" + ServiceUtils.generateUUID(new String(password) + userId);
        }

        int tenantId = getTenantIdByName(tenant);

        if (tenantId == -1) {
            tenantId = TenantRepository.create(tenant);
        }

        Users users = new Users(passHash, email, tenantId, name, "1", userId, groups);
        if (null != account.getAuthProfile() && null != account.getAuthProfile().get("verification_secret")) {
            users.setVerificationSecret(account.getAuthProfile().get("verification_secret").toString());
        }

        return users;
    }

    //createUserFromAccount with datapipeline
    private static Users createUserFromAccount(AuthAccount account, byte[] password,DataPipeline dataPipeline) throws SystemException {
        Map<String, Object> profile = account.getAuthProfile();
        String name = profile.get("name") != null ? profile.get("name").toString() : "";
        String email = profile.get("email") != null ? profile.get("email").toString() : "";
        List<String> groupName = (List<String>) profile.get("groups");
        List<Groups> groups = groupName.stream()
                .map(Groups::new)
                .collect(Collectors.toList());
        String tenant = dataPipeline.rp.getTenant().getName();
        String userId = account.getUserId();
        String passHash = null;
        if (null != password) {
            passHash = "[#]" + ServiceUtils.generateUUID(new String(password) + userId);
        }
        Timestamp createdDate = new Timestamp(System.currentTimeMillis());
        System.out.println("create user complete........... ");

        int tenantId = getTenantIdByName(tenant);

        if (tenantId == -1) {
            tenantId = TenantRepository.create(tenant);
        }

        Users users = new Users(passHash, email, tenantId, name, "1", userId, groups, createdDate, createdDate, 0);
        if (null != account.getAuthProfile() && null != account.getAuthProfile().get("verification_secret")) {
            users.setVerificationSecret(account.getAuthProfile().get("verification_secret").toString());
        }
        return users;
    }

    public static void updateUser(AuthAccount account, final byte[] pass) throws SystemException {
        Users userFromAccount = createUserFromAccount(account, pass);
        Timestamp modifiedDate = new Timestamp(System.currentTimeMillis());
        userFromAccount.setModified_date(modifiedDate);
        UsersRepository.updateUser(userFromAccount.getEmail(), userFromAccount);
    }

    //update user with dataPipeline
    public static void updateUser(AuthAccount account, final byte[] pass,DataPipeline dataPipeline) throws SystemException {
        Users userFromAccount = createUserFromAccount(account, pass,dataPipeline);
        Timestamp modifiedDate = new Timestamp(System.currentTimeMillis());
        userFromAccount.setModified_date(modifiedDate);
        UsersRepository.updateUser(userFromAccount.getEmail(), userFromAccount);
    }

    //update with pipeline
    public static void updateUser(AuthAccount account, final byte[] pass, String status,DataPipeline dataPipeline) throws SystemException {
        Users userFromAccount = createUserFromAccount(account, pass,dataPipeline);
        Timestamp modifiedDate = new Timestamp(System.currentTimeMillis());
        userFromAccount.setModified_date(modifiedDate);
        userFromAccount.setStatus(status);
        UsersRepository.updateUser(userFromAccount.getEmail(), userFromAccount);
    }
    public static void updateUser(AuthAccount account, final byte[] pass, String status) throws SystemException {
        Users userFromAccount = createUserFromAccount(account, pass);
        Timestamp modifiedDate = new Timestamp(System.currentTimeMillis());
        userFromAccount.setModified_date(modifiedDate);
        userFromAccount.setStatus(status);
        UsersRepository.updateUser(userFromAccount.getEmail(), userFromAccount);
    }

    public static void updateVerificationSecret(String email, String verification) throws Exception {
        UsersRepository.updateVerificationSecret(email, verification);
    }

    public static void removeUser(String id) throws SystemException {
        UsersRepository.deleteUser(id);
    }

    public static UserProfileManager create() throws SystemException {
        if (upm == null)
            upm = new UserProfileManager();
        return upm;
    }

    public static UserProfileManager getUserProfileManager() {
        return upm;
    }

    private UserProfileManager() throws SystemException {
        //getUsers();
    }

    @Override
    public Account verify(Account account) {
        return account;
    }

    @Override
    public AuthAccount verify(String id, Credential credential) {
        AuthAccount account = getAccount(id, null);
        try {
            if (account != null && verifyCredential(account, credential)) {
                return account;
            }
        } catch (SystemException e) {
            ServiceUtils.printException("Login exception for " + id, e);
        }

        return null;
    }

    @Override
    public Account verify(Credential credential) {
        // TODO Auto-generated method stub
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential) throws SystemException {
        if (credential instanceof PasswordCredential) {
            char[] password = ((PasswordCredential) credential).getPassword();
            String userId = account.getPrincipal().getName();
            Map<String, Object> usersMap = getUserById(userId);
            Map<String, Object> user = (Map<String, Object>) usersMap.get(userId);
            if (user == null) {
                return false;
            }
            if (user.get("status") != null && user.get("status").equals("0")) {
                return false;
            }
            if (user.get("password") == null)
                return false;
            char[] expectedPassword = user.get("password").toString().toCharArray();
            String pass = new String(password);
            String passHash = "[#]" + ServiceUtils.generateUUID(pass + userId);
            return Arrays.equals(passHash.toCharArray(), expectedPassword);
        }
        return false;
    }

    public static AuthAccount getAccount(UserProfile up) {
        if (up == null)
            return null;
        String id = (String) up.getId();
        if (up.getAttribute("email") != null)
            id = (String) up.getAttribute("email");
        return getAccount(id, up);
    }

    public static AuthAccount getAccount(final String id, final UserProfile up) {
        Map<String, Object> usersMap = null;
        try {
            usersMap = getUserById(id);
        } catch (SystemException e) {
            ServiceUtils.printException("Could not load users list: " + id, e);
            return null;
        }
        Map<String, Object> user = (Map<String, Object>) usersMap.get(id);

        if (user != null) {
            Map<String, Object> profile = (Map<String, Object>) user.get("profile");
            String tenant = (String) profile.get("tenant");
            if (up != null && up.getAttribute("access_token") != null && profile == null) {
                profile = createDefaultProfile(up, tenant);
                // profile.put("tenant", tenant);
            }

            AuthAccount authAccount = new AuthAccount(id);
            final Map<String, Object> profle = profile;
            authAccount.setProfile(profle);
            return authAccount;
        } else {
            final Map<String, Object> profile = createDefaultProfile(up, null);
            AuthAccount authAccount = new AuthAccount(id);
            authAccount.setProfile(profile);
            // authAccount.getAuthProfile().put("groups", authAccount);
            return authAccount;
        }
    }

    public static Map<String, Object> createDefaultProfile(UserProfile up, String tenant) {
        Map<String, Object> profile = new HashMap<String, Object>();
        Map<String, Object> jwtMap = null;
        //String tenant = null;
        List<String> groups = new ArrayList<String>();
        String UUID = null;
        String name = null;
        String userId = null;
        String email = null;
        Long creationTimestamp = null;
        try {
            BearerAccessToken bat = (BearerAccessToken) up.getAttribute("access_token");
            if (bat != null) {
                String jwt = bat.getValue();
                if (jwt != null) {
                    try {
                        jwtMap = JWTParser.parse(jwt).getJWTClaimsSet().toJSONObject();
                    } catch (Exception ignored) {
                    }
                    if (jwtMap != null) {
                        groups = (List<String>) jwtMap.get("groups");
                        UUID = (null == up.getAttribute("UUID")) ? null : up.getAttribute("UUID").toString();
                        name = (null == up.getAttribute("name")) ? null : up.getAttribute("name").toString();
                        userId = (null == up.getAttribute("userId")) ? null : up.getAttribute("userId").toString();
                        email = (null == up.getAttribute("email")) ? null : up.getAttribute("email").toString();
                        creationTimestamp = (null == up.getAttribute("creationTimestamp")) ? null : Long.parseLong(up.getAttribute("creationTimestamp").toString());
                        tenant = (String) jwtMap.get("tenant");
                        if (groups == null)
                            groups = (List<String>) jwtMap.get("groups");
                    }
                }
            } else {
                tenant = (String) up.getAttribute("tenant");
                UUID = (null == up.getAttribute("UUID")) ? null : up.getAttribute("UUID").toString();
                name = (null == up.getAttribute("name")) ? null : up.getAttribute("name").toString();
                userId = (null == up.getAttribute("userId")) ? null : up.getAttribute("userId").toString();
                email = (null == up.getAttribute("email")) ? null : up.getAttribute("email").toString();
                creationTimestamp = (null == up.getAttribute("creationTimestamp")) ? null : Long.parseLong(up.getAttribute("creationTimestamp").toString());
                if (up.getAttribute("groups") != null)
                    groups = (List<String>) up.getAttribute("groups");
            }
        } catch (Exception e) {
            ServiceUtils.printException("Failed while get token from UserProfile", e);
        }
        // String groups[] = { "Guest" };
        if (groups != null && groups.size() == 0) {
            groups.add("guest");
            groups.add("default");
        }
        profile.put("groups", groups);
        profile.put("UUID", UUID);
        profile.put("name", name);
        profile.put("userId", userId);
        profile.put("email", email);
        profile.put("creationTimestamp", creationTimestamp);
        if (tenant != null)
            profile.put("tenant", tenant);
        return profile;
    }

    public static final UserProfile SYSTEM_PROFILE = new UserProfile() {

        @Override
        public String getId() {
            // TODO Auto-generated method stub
            return "SYSTEM";
        }

        @Override
        public void setId(String id) {
            // TODO Auto-generated method stub

        }

        @Override
        public String getTypedId() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getUsername() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Object getAttribute(String name) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Map<String, Object> getAttributes() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean containsAttribute(String name) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void addAttribute(String key, Object value) {
            // TODO Auto-generated method stub

        }

        @Override
        public void removeAttribute(String key) {
            // TODO Auto-generated method stub

        }

        @Override
        public void addAuthenticationAttribute(String key, Object value) {
            // TODO Auto-generated method stub

        }

        @Override
        public void removeAuthenticationAttribute(String key) {
            // TODO Auto-generated method stub

        }

        @Override
        public void addRole(String role) {
            // TODO Auto-generated method stub

        }

        @Override
        public void addRoles(Collection<String> roles) {
            // TODO Auto-generated method stub

        }

        @Override
        public Set<String> getRoles() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void addPermission(String permission) {
            // TODO Auto-generated method stub

        }

        @Override
        public void addPermissions(Collection<String> permissions) {
            // TODO Auto-generated method stub

        }

        @Override
        public Set<String> getPermissions() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isRemembered() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void setRemembered(boolean rme) {
            // TODO Auto-generated method stub

        }

        @Override
        public String getClientName() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setClientName(String clientName) {
            // TODO Auto-generated method stub

        }

        @Override
        public String getLinkedId() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setLinkedId(String linkedId) {
            // TODO Auto-generated method stub

        }

        @Override
        public boolean isExpired() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public Principal asPrincipal() {
            // TODO Auto-generated method stub
            return null;
        }
    };

    public static void migrationProfiles() throws SystemException {

        String configFolderPath = PropertyManager.getConfigFolderPath();
        File dbFile = new File(configFolderPath + "profiles.db");
        boolean isNewDatabase = !dbFile.exists();

        if (isNewDatabase) {
            createProfileDB();
        }


        String absoluteFilePath = PropertyManager.getConfigFolderPath() + "profiles.json";
        File file = new File(absoluteFilePath);

        if (file.exists()) {
			byte[] bytes = PropertyManager.readConfigurationFile("profiles.json");
			String json = new String(bytes);
            final Map<String, Object> map = ServiceUtils.jsonToMap(json);
            final Map<String, Object> usersMap = (Map<String, Object>) map.get("users");

            if (usersMap != null) {
                try (Connection connection = SQL.getProfileConnection(true)) {
					try {
						for (Map.Entry<String, Object> userMap: usersMap.entrySet()) {

							String username = userMap.getKey();
							Object userObj = userMap.getValue();

							Map<String, Object> user = (Map<String, Object>) userObj;
							Map<String, Object> profile = (Map<String, Object>) user.get("profile");
							try {
								String insertUserSQL = "INSERT INTO users (password, email, tenant_id, status, user_id, name, deleted, created_date, modified_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
								String insertUserGroupMappingSQL = "INSERT INTO user_group_mapping (user_id, group_id) VALUES (?, ?)";

								int tenantId = getOrCreateTenant(profile.get("tenant").toString(), connection);

								try (PreparedStatement userStatement = connection.prepareStatement(insertUserSQL);
									 PreparedStatement mappingStatement = connection.prepareStatement(insertUserGroupMappingSQL)) {

									userStatement.setString(1, user.get("password") != null ? user.get("password").toString() : "");
									userStatement.setString(2, profile.get("email") != null ? profile.get("email").toString() : username);
									userStatement.setInt(3, tenantId);
									userStatement.setString(4, user.get("status") != null ? user.get("status").toString() : "0");
									userStatement.setString(5, username);
									userStatement.setString(6, profile.get("name") != null ? profile.get("name").toString() : username);
									userStatement.setBoolean(7, false);
                                    userStatement.setDate(8, new Date(new java.util.Date().getTime()));
                                    userStatement.setDate(9, new Date(new java.util.Date().getTime()));

									userStatement.executeUpdate();
                                    ResultSet generatedKeys = userStatement.getGeneratedKeys();
                                    generatedKeys.next();
                                    int id = generatedKeys.getInt(1);

									List<String> userGroups = (List<String>) profile.get("groups");
									if (userGroups != null) {
										for (String groupName : userGroups) {
											int groupId = getOrCreateGroup(groupName, tenantId, connection);

											if (!doesMappingExist(username, groupId, connection)) {
												mappingStatement.setInt(1, id);
												mappingStatement.setInt(2, groupId);
												mappingStatement.executeUpdate();
											}
										}
									}
								}
							} catch (SQLException e) {
								throw e;
							}
						}
						SQL.commitTransaction(connection);
                        /**
                         * Rename profile.json
                         */
                        boolean renameSuccess = file.renameTo(new File(PropertyManager.getConfigFolderPath() + "profiles-v1.4.9.json"));
                        if (renameSuccess) {
                            LOGGER.info("File renamed successfully to profiles-v1.4.9.json.");
                        } else {
                            LOGGER.warn("Failed to rename the file to profiles-v1.4.9.json.");
                        }
					} catch (Exception e) {
						e.printStackTrace();
						try {
							SQL.rollbackTransaction(connection);
						} catch (Exception ex) {
							throw new RuntimeException(ex);
						}
					}
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
		}
    }

    public static void createProfileDB() {
        String configFolderPath = PropertyManager.getConfigFolderPath();
        String connectionURL = "jdbc:sqlite:" + configFolderPath + "profiles.db";

        try (Connection connection = DriverManager.getConnection(connectionURL)) {
            if (connection != null) {
                DatabaseMetaData meta = connection.getMetaData();

                try (Statement statement = connection.createStatement()) {
                    String createGroupsTable = "CREATE TABLE IF NOT EXISTS groups (" +
                            "group_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "name TEXT NOT NULL," +
                            "tenant_id INTEGER," +
                            "created_date DATETIME," +
                            "modified_date DATETIME," +
                            "deleted BOOLEAN" +
                            ")";
                    statement.executeUpdate(createGroupsTable);

                    String createTenantTable = "CREATE TABLE IF NOT EXISTS tenant (" +
                            "tenant_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                            "name TEXT," +
                            "created_date DATETIME," +
                            "modified_date DATETIME," +
                            "deleted BOOLEAN" +
                            ")";
                    statement.executeUpdate(createTenantTable);

                    String createUserGroupMappingTable = "CREATE TABLE IF NOT EXISTS user_group_mapping (" +
                            "mapping_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "user_id TEXT," +
                            "group_id INTEGER" +
                            ")";
                    statement.executeUpdate(createUserGroupMappingTable);

                    String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                            "password TEXT," +
                            "email TEXT," +
                            "tenant_id INTEGER NOT NULL," +
                            "status TEXT," +
                            "user_id TEXT NOT NULL," +
                            "name TEXT," +
                            "deleted BOOLEAN," +
                            "created_date DATETIME," +
                            "modified_date DATETIME," +
                            "verification_secret TEXT" +
                            ")";
                    statement.executeUpdate(createUsersTable);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
