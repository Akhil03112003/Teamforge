-- Use only the isolated benchmark PGOPTIONS/search path from Load-Environment.ps1.
-- psql -X -qAt -v benchmark_event=UUID -f benchmarks/explain-board.sql
BEGIN READ ONLY;
SELECT minimum_team_size AS minimum_size, maximum_team_size AS maximum_size
FROM hackathons WHERE id=:'benchmark_event'::uuid
\gset
EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT JSON)
WITH bounds AS (SELECT :minimum_size::int min_size,:maximum_size::int max_size),
paged AS MATERIALIZED (
  SELECT t.id,lower(t.name) sort_name,n.member_count
  FROM teams t
  CROSS JOIN LATERAL (SELECT count(*)::int member_count FROM team_members m WHERE m.team_id=t.id) n
  CROSS JOIN bounds
  WHERE t.hackathon_id=:'benchmark_event'::uuid
    AND (lower(t.name) COLLATE "C",t.id)>('' COLLATE "C",'00000000-0000-0000-0000-000000000000'::uuid)
  ORDER BY lower(t.name) COLLATE "C",t.id LIMIT 51
)
SELECT t.id,t.name,t.description,t.leader_user_id,coalesce(p.display_name,'Participant') leader_name,
  b.min_size,b.max_size,e.member_count,(b.max_size-e.member_count)::int open_seats,
  CASE WHEN e.member_count<b.min_size THEN 'INCOMPLETE' ELSE 'READY' END status,e.sort_name
FROM paged e JOIN teams t ON t.id=e.id CROSS JOIN bounds b
LEFT JOIN profiles p ON p.user_id=t.leader_user_id
ORDER BY e.sort_name COLLATE "C",e.id;
ROLLBACK;
