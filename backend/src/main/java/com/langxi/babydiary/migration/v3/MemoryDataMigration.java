package com.langxi.babydiary.migration.v3;

import java.sql.Connection;

final class MemoryDataMigration extends V3JdbcMigration {
    private static final String NAMESPACE = "6f589db1-7ae8-5f24-b517-fbdbd84eaf12";

    void migrate(Connection source, Connection target) throws Exception {
        albumGroups(source, target);
        albums(source, target);
        albumMedia(source, target);
        anniversaries(source, target);
    }

    private void albumGroups(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT group_id,user_id,space_id,name,sort,created_at,updated_at FROM album_group ORDER BY group_id",
                "INSERT INTO album_group(group_id,public_id,space_id,name,sort_order,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("group_id");
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.uuid(NAMESPACE, "album-group:" + id));
                    insert.setLong(3, row.getLong("space_id"));
                    insert.setString(4, row.getString("name"));
                    insert.setInt(5, row.getInt("sort"));
                    insert.setLong(6, row.getLong("user_id"));
                    setTime(insert, 7, requiredTime(row, "created_at"));
                    setTime(insert, 8, requiredTime(row, "updated_at"));
                });
    }

    private void albums(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT album_id,group_id,user_id,space_id,name,description,type,cover_asset_id,sort,created_at,updated_at FROM album ORDER BY album_id",
                "INSERT INTO album(album_id,public_id,space_id,group_id,created_by,name,description,type,cover_asset_id,sort_order,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("album_id");
                    String type = "AI".equalsIgnoreCase(row.getString("type")) ? "AI" : "CUSTOM";
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.uuid(NAMESPACE, "album:" + id));
                    insert.setLong(3, row.getLong("space_id"));
                    setLong(insert, 4, row, "group_id");
                    insert.setLong(5, row.getLong("user_id"));
                    insert.setString(6, row.getString("name"));
                    setString(insert, 7, row.getString("description"));
                    insert.setString(8, type);
                    setLong(insert, 9, row, "cover_asset_id");
                    insert.setInt(10, row.getInt("sort"));
                    setTime(insert, 11, requiredTime(row, "created_at"));
                    setTime(insert, 12, requiredTime(row, "updated_at"));
                });
    }

    private void albumMedia(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT a.space_id,am.album_id,am.asset_id,am.sort,am.created_at FROM album_media am JOIN album a ON a.album_id=am.album_id ORDER BY am.album_id,am.sort,am.asset_id",
                "INSERT INTO album_media(space_id,album_id,asset_id,position,created_at) VALUES(?,?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("space_id"));
                    insert.setLong(2, row.getLong("album_id"));
                    insert.setLong(3, row.getLong("asset_id"));
                    insert.setInt(4, row.getInt("sort"));
                    setTime(insert, 5, requiredTime(row, "created_at"));
                });
    }

    private void anniversaries(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT anniversary_id,user_id,space_id,title,date,description,cover_asset_id,sort,created_at,updated_at FROM anniversary ORDER BY anniversary_id",
                "INSERT INTO anniversary(anniversary_id,public_id,space_id,created_by,title,anniversary_date,description,cover_asset_id,sort_order,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("anniversary_id");
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.uuid(NAMESPACE, "anniversary:" + id));
                    insert.setLong(3, row.getLong("space_id"));
                    insert.setLong(4, row.getLong("user_id"));
                    insert.setString(5, row.getString("title"));
                    setDate(insert, 6, V3MigrationSupport.date(row, "date"));
                    setString(insert, 7, row.getString("description"));
                    setLong(insert, 8, row, "cover_asset_id");
                    insert.setInt(9, row.getInt("sort"));
                    setTime(insert, 10, requiredTime(row, "created_at"));
                    setTime(insert, 11, requiredTime(row, "updated_at"));
                });
    }
}
