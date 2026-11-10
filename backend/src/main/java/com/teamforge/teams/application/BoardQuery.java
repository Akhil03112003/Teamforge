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
    private final JdbcTemplate jdbc;private final TeamStore teams;
    public BoardQuery(JdbcTemplate jdbc,TeamStore teams){this.jdbc=jdbc;this.teams=teams;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page board(UUID event,String cursor,int limit,String skill,boolean openSeats,String status,String sort){
        return board(event,cursor,limit,skill,openSeats,status,sort,null);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page board(UUID event,String cursor,int limit,String skill,boolean openSeats,String status,String sort,String query){
        teams.event(event);
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
        var params=new ArrayList<Object>();params.add(event);
        String filters="";
        if(skill!=null){filters+=" AND t.desired_skills @> ARRAY[?]::text[]";params.add(skill);}
        if(query!=null && !query.isBlank()){
            if(query.length()>120)invalid();
            filters+=" AND strpos(lower(t.name || ' ' || t.description),lower(?))>0";params.add(query.strip());
        }
        String paging=sort.equals("id")?"id>?":"(sort_name COLLATE \"C\",id)>(? COLLATE \"C\",?)";
        if(sort.equals("name"))params.add(afterName);params.add(after);
        String post=(openSeats?" AND open_seats>0":"")+(status==null?"":" AND status=?");
        if(status!=null)params.add(status);params.add(limit+1);
        String sql="""
            WITH board AS (
              SELECT t.id,t.name,t.description,t.leader_user_id,coalesce(p.display_name,'Participant') leader_name,
                h.minimum_team_size min_size,h.maximum_team_size max_size,count(m.id)::int member_count,
                (h.maximum_team_size-count(m.id))::int open_seats,lower(t.name) sort_name,
                CASE WHEN count(m.id)<h.minimum_team_size THEN 'INCOMPLETE' ELSE 'READY' END status
              FROM teams t JOIN hackathons h ON h.id=t.hackathon_id
              LEFT JOIN profiles p ON p.user_id=t.leader_user_id LEFT JOIN team_members m ON m.team_id=t.id
              WHERE t.hackathon_id=?
            """+filters+" GROUP BY t.id,h.minimum_team_size,h.maximum_team_size,p.display_name) SELECT * FROM board WHERE "+paging+post+
            " ORDER BY "+(sort.equals("id")?"id":"sort_name COLLATE \"C\",id")+" LIMIT ?";
        var rows=jdbc.query(sql,(r,i)->new Summary(r.getObject("id",UUID.class),r.getString("name"),r.getString("description"),
            r.getObject("leader_user_id",UUID.class),r.getString("leader_name"),r.getString("status"),r.getInt("min_size"),r.getInt("max_size"),r.getInt("member_count"),r.getInt("open_seats")),params.toArray());
        boolean more=rows.size()>limit;var items=List.copyOf(rows.subList(0,Math.min(rows.size(),limit)));String next=null;
        if(more){var last=items.getLast();next=last.id().toString();if(sort.equals("name")){
            // PostgreSQL supplies its collation's lower-case key, not Java's potentially different folding.
            String name=jdbc.queryForObject("SELECT lower(name) FROM teams WHERE id=?",String.class,last.id());
            next=Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8))+"."+next;
        }}
        return new Page(items,next);
    }
    private static void invalid(){throw new ApiException(400,"VALIDATION_ERROR","Invalid board filters, sort or cursor.");}
}
