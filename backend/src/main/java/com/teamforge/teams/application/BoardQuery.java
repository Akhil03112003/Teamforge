package com.teamforge.teams.application;

import com.teamforge.platform.api.ApiException;
import com.teamforge.teams.persistence.TeamStore;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class BoardQuery {
    public record Summary(UUID id,String name,String description,UUID leaderId,String leaderDisplayName,String status,
        int minSize,int maxSize,int memberCount,int openSeats){}
    public record Page(List<Summary> items,String nextCursor){}
    private record Ranked(Summary summary,String sortName){}
    private final JdbcTemplate jdbc;private final TeamStore teams;
    public BoardQuery(JdbcTemplate jdbc,TeamStore teams){this.jdbc=jdbc;this.teams=teams;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page board(UUID event,String cursor,int limit,String skill,boolean openSeats,String status,String sort){
        return board(event,cursor,limit,skill,openSeats,status,sort,null);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page board(UUID event,String cursor,int limit,String skill,boolean openSeats,String status,String sort,String query){
        var bounds=teams.event(event);
        if(limit<1 || limit>50 || !Set.of("id","name").contains(sort) || status!=null && !Set.of("READY","INCOMPLETE").contains(status))invalid();
        if(skill!=null){skill=skill.strip().toLowerCase(Locale.ROOT);if(!skill.matches("[a-z0-9][a-z0-9 +#./-]{0,39}"))invalid();}
        UUID after=new UUID(0,0);String afterName="";
        if(cursor!=null){
            try{
                if(cursor.length()>300)invalid();
                if(sort.equals("id"))after=UUID.fromString(cursor);
                else{int split=cursor.lastIndexOf('.');afterName=new String(Base64.getUrlDecoder().decode(cursor.substring(0,split)),StandardCharsets.UTF_8);after=UUID.fromString(cursor.substring(split+1));}
            }catch(IllegalArgumentException|IndexOutOfBoundsException bad){invalid();}
        }
        var params=new ArrayList<Object>();params.add(bounds.minSize());params.add(bounds.maxSize());params.add(event);
        String filters="";
        if(skill!=null){filters+=" AND t.desired_skills @> ARRAY[?]::text[]";params.add(skill);}
        if(query!=null && !query.isBlank()){
            if(query.length()>120)invalid();
            filters+=" AND strpos(lower(t.name || ' ' || t.description),lower(?))>0";params.add(query.strip());
        }
        String paging=sort.equals("id")?"t.id>?":"(lower(t.name) COLLATE \"C\",t.id)>(? COLLATE \"C\",?)";
        if(sort.equals("name"))params.add(afterName);params.add(after);
        String post=(openSeats?" AND member_count<max_size":"")+(status==null?"":" AND CASE WHEN member_count<min_size THEN 'INCOMPLETE' ELSE 'READY' END=?");
        if(status!=null)params.add(status);params.add(limit+1);
        // One SQL statement: indexed roster counts filter ordered candidates before
        // LIMIT; the materialized page bounds all subsequent wide/profile joins.
        String sql="""
            WITH bounds AS (SELECT ?::int min_size,?::int max_size),
            paged AS MATERIALIZED (
              SELECT t.id,lower(t.name) sort_name,n.member_count
              FROM teams t
              CROSS JOIN LATERAL (SELECT count(*)::int member_count FROM team_members m WHERE m.team_id=t.id) n
              CROSS JOIN bounds
              WHERE t.hackathon_id=?
            """+filters+" AND "+paging+post+
            " ORDER BY "+(sort.equals("id")?"t.id":"lower(t.name) COLLATE \"C\",t.id")+" LIMIT ?) "+"""
            SELECT t.id,t.name,t.description,t.leader_user_id,coalesce(p.display_name,'Participant') leader_name,
              b.min_size,b.max_size,e.member_count,(b.max_size-e.member_count)::int open_seats,
              CASE WHEN e.member_count<b.min_size THEN 'INCOMPLETE' ELSE 'READY' END status,e.sort_name
            FROM paged e JOIN teams t ON t.id=e.id CROSS JOIN bounds b
            LEFT JOIN profiles p ON p.user_id=t.leader_user_id
            """+" ORDER BY "+(sort.equals("id")?"e.id":"e.sort_name COLLATE \"C\",e.id");
        var rows=jdbc.query(sql,(r,i)->new Ranked(new Summary(r.getObject("id",UUID.class),r.getString("name"),r.getString("description"),
            r.getObject("leader_user_id",UUID.class),r.getString("leader_name"),r.getString("status"),r.getInt("min_size"),r.getInt("max_size"),r.getInt("member_count"),r.getInt("open_seats")),r.getString("sort_name")),params.toArray());
        boolean more=rows.size()>limit;var items=rows.subList(0,Math.min(rows.size(),limit)).stream().map(Ranked::summary).toList();String next=null;
        if(more){var last=rows.get(limit-1);next=last.summary().id().toString();if(sort.equals("name")){
            // Reuse PostgreSQL's exact key from the last displayed row, not the sentinel.
            next=Base64.getUrlEncoder().withoutPadding().encodeToString(last.sortName().getBytes(StandardCharsets.UTF_8))+"."+next;
        }}
        return new Page(items,next);
    }
    private static void invalid(){throw new ApiException(400,"VALIDATION_ERROR","Invalid board filters, sort or cursor.");}
}
