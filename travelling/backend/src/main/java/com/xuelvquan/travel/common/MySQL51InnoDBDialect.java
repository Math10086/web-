package com.xuelvquan.travel.common;


public class MySQL51InnoDBDialect extends org.hibernate.community.dialect.MySQLLegacyDialect {

    @Override
    public String getTableTypeString() {
        return " ENGINE=InnoDB";
    }
}
