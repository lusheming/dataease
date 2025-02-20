package io.dataease.datasource.provider;


import io.dataease.dataset.utils.TableUtils;
import io.dataease.datasource.dao.auto.entity.CoreDeEngine;
import io.dataease.datasource.request.EngineRequest;
import io.dataease.datasource.type.H2;
import io.dataease.extensions.datasource.dto.DatasourceDTO;
import io.dataease.extensions.datasource.dto.TableField;
import io.dataease.extensions.datasource.vo.DatasourceConfiguration;
import io.dataease.utils.BeanUtils;
import io.dataease.utils.JsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

@Service("h2Engine")
public class H2EngineProvider extends EngineProvider {


    public void exec(EngineRequest engineRequest) throws Exception {
        DatasourceConfiguration configuration = JsonUtil.parseObject(engineRequest.getEngine().getConfiguration(), H2.class);
        int queryTimeout = configuration.getQueryTimeout();
        DatasourceDTO datasource = new DatasourceDTO();
        BeanUtils.copyBean(datasource, engineRequest.getEngine());
        try (Connection connection = getConnection(datasource); Statement stat = getStatement(connection, queryTimeout)) {
            PreparedStatement preparedStatement = connection.prepareStatement(engineRequest.getQuery());
            preparedStatement.setQueryTimeout(queryTimeout);
            Boolean result = preparedStatement.execute();
        } catch (Exception e) {
            throw e;
        }
    }

    private static final String creatTableSql =
            "CREATE TABLE IF NOT EXISTS `TABLE_NAME`" +
                    "Column_Fields;";


    @Override
    public String createView(String name, String viewSQL) {
        return "CREATE or replace view " + name + " AS (" + viewSQL + ")";
    }

    @Override
    public String insertSql(String name, List<String[]> dataList, int page, int pageNumber) {
        String insertSql = "INSERT INTO `TABLE_NAME` VALUES ".replace("TABLE_NAME", name);
        StringBuffer values = new StringBuffer();

        Integer realSize = page * pageNumber < dataList.size() ? page * pageNumber : dataList.size();
        for (String[] strings : dataList.subList((page - 1) * pageNumber, realSize)) {
            String[] strings1 = new String[strings.length];
            for (int i = 0; i < strings.length; i++) {
                if (StringUtils.isEmpty(strings[i])) {
                    strings1[i] = null;
                } else {
                    strings1[i] = strings[i].replace("'", "\\'");
                }
            }
            values.append("('").append(String.join("','", Arrays.asList(strings1)))
                    .append("'),");
        }
        return (insertSql + values.substring(0, values.length() - 1)).replaceAll("'null'", "null");
    }


    @Override
    public String dropTable(String name) {
        return "DROP TABLE IF EXISTS `" + name + "`";
    }

    @Override
    public String dropView(String name) {
        return "DROP VIEW IF EXISTS `" + name + "`";
    }

    @Override
    public String replaceTable(String name) {
        return "ALTER TABLE `FROM_TABLE` rename to `FROM_TABLE_tmp`; ALTER TABLE `TO_TABLE` rename to `FROM_TABLE`; DROP TABLE IF EXISTS `FROM_TABLE_tmp`;".replace("FROM_TABLE", name).replace("TO_TABLE", TableUtils.tmpName(name));
    }


    @Override
    public String createTableSql(String tableName, List<TableField> tableFields, CoreDeEngine engine) {
        String dorisTableColumnSql = createTableSql(tableFields);
        return creatTableSql.replace("TABLE_NAME", tableName).replace("Column_Fields", dorisTableColumnSql);
    }

    private String createTableSql(final List<TableField> tableFields) {
        StringBuilder Column_Fields = new StringBuilder("`");
        for (TableField tableField : tableFields) {
            Column_Fields.append(tableField.getName()).append("` ");
            int size = tableField.getPrecision() * 4;
            switch (tableField.getDeType()) {
                case 0:
                    Column_Fields.append("longtext").append(",`");
                    break;
                case 1:
                    size = size < 50 ? 50 : size;
                    if (size < 65533) {
                        Column_Fields.append("varchar(length)".replace("length", String.valueOf(tableField.getPrecision()))).append(",`");
                    } else {
                        Column_Fields.append("longtext").append(",`");
                    }
                    break;
                case 2:
                    Column_Fields.append("bigint(20)").append(",`");
                    break;
                case 3:
                    Column_Fields.append("varchar(100)").append(",`");
                    break;
                case 4:
                    Column_Fields.append("TINYINT(length)".replace("length", String.valueOf(tableField.getPrecision()))).append(",`");
                    break;
                default:
                    if (size < 65533) {
                        Column_Fields.append("varchar(length)".replace("length", String.valueOf(tableField.getPrecision()))).append(",`");
                    } else {
                        Column_Fields.append("longtext").append(",`");
                    }
                    break;
            }
        }

        Column_Fields = new StringBuilder(Column_Fields.substring(0, Column_Fields.length() - 2));
        Column_Fields = new StringBuilder("(" + Column_Fields + ")\n");
        return Column_Fields.toString();
    }
}
