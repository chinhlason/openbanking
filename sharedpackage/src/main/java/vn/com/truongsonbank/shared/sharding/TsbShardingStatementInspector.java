package vn.com.truongsonbank.shared.sharding;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.regex.Pattern;

public class TsbShardingStatementInspector implements StatementInspector {
    @Override
    public String inspect(String sql) {
        return TsbShardContext.current()
                .map(route -> rewrite(sql, route.logicalTable(), route.physicalTable()))
                .orElse(sql);
    }

    private String rewrite(String sql, String logicalTable, String physicalTable) {
        if (logicalTable.equals(physicalTable)) {
            return sql;
        }
        String quoted = "`?" + Pattern.quote(logicalTable) + "`?";
        return sql.replaceAll("(?i)(?<![\\w`])" + quoted + "(?![\\w`])", physicalTable);
    }
}
