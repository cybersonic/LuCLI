<cfscript>

    varNoScope = "INSERT IGNORE INTO UserRoles (userid, role) VALUES #sqlValues.ToList()#";
    
    varNoScope &= "Limit 1";

    
    QueryExecute( varNoScope );
    QueryExecute( "SELECT * FROM users WHERE NAME = 'Bobby Drop Tables'" );

        sql="Elvis, not related to a qury";

    function bla(){
        sql = "SELECT * FROM users WHERE NAME = 'Bobby Drop Tables'";
        QueryExecute( sql );
        
    }
    function bla2(input){
        sql = "SELECT * FROM users WHERE NAME = 'Bobby TTTAAA'";
        QueryExecute( sql );

    }

    local.maQuery = "INSERT IGNORE INTO UserRoles (userid, role) VALUES #sqlValues.ToList()#";
    echo("My day is #Now()#");
    local.maQuery &= "AND ELVIS=true";
    local.maQuery &= "AND Delete == true ";
    local.maQuery &= "AND Delete == true ";

    bla2( local.maQuery );


    QueryExecute( sql=maQuery, params=sqlParams, options={ datasource: application.Datasource, result:"local.rUserRoles"}  );
    QueryExecute( sql="Select bobby from DropTables", params=sqlParams, options={ datasource: application.Datasource, result:"local.rUserRoles"}  );
</cfscript>

<cfquery name="elvis">
    SELECT * FROM users WHERE NAME = "Bobby Drop Tables"
</cfquery>

<Cfquery name="elvis2">
    #local.maQuery#    
</Cfquery>