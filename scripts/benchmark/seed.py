"""Opt-in, constraint-preserving PostgreSQL fixtures. Requires Python, psql and migrated DB.

No deletion/reset operation exists. Credentials come from PG* environment variables.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import uuid
import zipfile

VERSION = 'teamforge-m8a-v1'
NAMESPACE = uuid.UUID('0f01e94b-226b-4c61-a870-01c066222890')
EVENT = str(uuid.uuid5(NAMESPACE, 'hackathon'))
STAMP = '2026-01-01T00:00:00Z'
SKILLS = ['java', 'typescript', 'react', 'python', 'postgresql', 'design', 'testing', 'devops', 'accessibility', 'data']
ZONES = ['UTC', 'Asia/Kolkata', 'Europe/London', 'Europe/Berlin', 'America/New_York', 'America/Los_Angeles', 'Asia/Tokyo', 'Australia/Sydney']


def identity(kind, number):
    return str(uuid.uuid5(NAMESPACE, f'{kind}:{number}'))


def literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def data(password_hash):
    tables = {name: [] for name in ['users', 'profiles', 'profile_skills', 'profile_availability', 'event_participants', 'teams', 'team_members', 'audit_events']}
    for i in range(1, 10001):
        user = identity('user', i)
        tables['users'].append((user, f'bench{i:05}@example.test', password_hash, STAMP))
        tables['profiles'].append((user, f'Benchmark participant {i:05}', ZONES[(i-1) % len(ZONES)], 0))
        for skill in sorted({SKILLS[(i-1) % 10], SKILLS[(i+2) % 10], SKILLS[(i+4) % 10]}):
            tables['profile_skills'].append((user, skill))
        for slot in range(2):
            day = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'][(i+slot*2) % 7]
            start = 9 + (i % 3)*3
            tables['profile_availability'].append((user, slot, day, f'{start:02}:00:00', f'{start+3:02}:00:00'))
        tables['event_participants'].append((EVENT, user, STAMP))
    member = 0
    for i in range(1, 2001):
        team = identity('team', i)
        size = 1 + (i-1) % 5
        leader = identity('user', member+1)
        desired = '{' + ','.join(sorted({SKILLS[(i-1) % 10], SKILLS[(i+3) % 10]})) + '}'
        tables['teams'].append((team, EVENT, f'{["Circuit", "Orbit", "Studio", "Signal"][i % 4]} {i:04}',
                                f'Synthetic collaboration team {i:04}; building accessible hackathon tools.', desired, leader, STAMP))
        tables['audit_events'].append((identity('audit-created', i), EVENT, team, leader, 'TEAM_CREATED', 'TEAM', team, STAMP))
        for seat in range(size):
            member += 1
            user = identity('user', member)
            tables['team_members'].append((identity('membership', member), EVENT, team, user, STAMP))
            if seat:
                tables['audit_events'].append((identity('audit-admitted', member), EVENT, team, user, 'MEMBER_ADMITTED', 'USER', user, STAMP))
    return tables


def digest(tables):
    # Hash all inserted columns, in canonical table/row order, including the supplied hash.
    return hashlib.sha256(json.dumps(tables, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def psql(args, sql):
    environment = os.environ.copy()
    environment.setdefault('PGCONNECT_TIMEOUT', '5')
    environment['PGOPTIONS'] = '-c search_path=' + (getattr(args, 'schema', None) or 'public') + ' -c lock_timeout=10000 -c statement_timeout=120000'
    result = subprocess.run([args.psql, '-X', '-w', '-v', 'ON_ERROR_STOP=1', '-qAt', '-d', args.database],
                            input=sql, text=True, capture_output=True, env=environment)
    if result.returncode:
        # SQL errors can contain fixture values or connection credentials; keep private.
        raise RuntimeError('psql failed; check connectivity, migration/permissions and isolated fixture integrity. SQL output withheld.')
    return result.stdout.strip()


def guard(args):
    schema = getattr(args, 'schema', None)
    target = schema or args.database
    if not args.confirm_isolated or not re.fullmatch(r'teamforge_benchmark_[a-z0-9_]+', target or '') or not re.fullmatch(r'[a-z0-9_]+', args.database or ''):
        raise ValueError('Require --confirm-isolated and a dedicated teamforge_benchmark_* database or explicit isolated schema.')
    actual = psql(args, 'SELECT current_database();')
    if actual != args.database:
        raise ValueError('Connected database differs from explicit benchmark target.')
    if psql(args, 'SELECT current_schema();') != (schema or 'public'):
        raise ValueError('Isolated search path/schema not available; refusing access.')


def verify(args, expected=None):
    guard(args)
    # Counts alone do not prove determinism: compare every fixture table and column.
    if expected is None:
        password_hash = psql(args, "SELECT password_hash FROM users WHERE email='bench00001@example.test';")
        expected = data(password_hash)
    mismatches = []
    for table, rows in expected.items():
        columns = COLUMNS[table]
        actual = json.loads(psql(args, f"SELECT coalesce(json_agg(row_to_json(r)), '[]') FROM (SELECT {columns} FROM {table}) r;"))
        wanted = [dict(zip(columns.split(','), row)) for row in rows]
        # PostgreSQL renders timestamptz/time values differently from input literals.
        for records in (actual, wanted):
            for row in records:
                for field in ('created_at', 'registered_at', 'joined_at', 'occurred_at'):
                    if field in row:
                        from datetime import datetime, timezone
                        row[field] = datetime.fromisoformat(row[field].replace('Z', '+00:00')).astimezone(timezone.utc).isoformat()
                if 'desired_skills' in row and isinstance(row['desired_skills'], str):
                    row['desired_skills'] = row['desired_skills'].strip('{}').split(',')
        canonical = lambda values: sorted(json.dumps(row, sort_keys=True, separators=(',', ':')) for row in values)
        if canonical(actual) != canonical(wanted):
            mismatches.append(table)
    integrity = json.loads(psql(args, f"""
      SELECT json_build_object(
       'users',(SELECT count(*) FROM users),'profiles',(SELECT count(*) FROM profiles),
       'teams',(SELECT count(*) FROM teams),'memberships',(SELECT count(*) FROM team_members),
       'anchors',(SELECT count(*) FROM event_participants),'auditEvents',(SELECT count(*) FROM audit_events),
       'duplicateMemberships',(SELECT count(*) FROM (SELECT hackathon_id,user_id FROM team_members GROUP BY 1,2 HAVING count(*)>1) d),
       'invalidLeaders',(SELECT count(*) FROM teams t WHERE NOT EXISTS(SELECT 1 FROM team_members m WHERE m.team_id=t.id AND m.user_id=t.leader_user_id)),
       'capacityViolations',(SELECT count(*) FROM teams t JOIN hackathons h ON h.id=t.hackathon_id WHERE (SELECT count(*) FROM team_members m WHERE m.team_id=t.id)>h.maximum_team_size),
       'sizeDistribution',(SELECT json_object_agg(n,c) FROM (SELECT n,count(*) c FROM (SELECT count(*) n FROM team_members GROUP BY team_id) sizes GROUP BY n) groups));
    """))
    marker = psql(args, "SELECT version || ':' || fingerprint FROM benchmark_metadata WHERE singleton=true;")
    if marker != VERSION + ':' + digest(expected):
        mismatches.append('benchmark_metadata')
    event = psql(args, f"SELECT count(*) FROM hackathons WHERE id={literal(EVENT)} AND name='TeamForge Benchmark' AND minimum_team_size=2 AND maximum_team_size=5 AND active=true;")
    if event != '1':
        mismatches.append('benchmark_hackathon')
    if mismatches or any(integrity[key] != 0 for key in ['duplicateMemberships', 'invalidLeaders', 'capacityViolations']):
        raise ValueError('Fixture verification failed: ' + ', '.join(mismatches))
    return {'dataset': VERSION, 'database': args.database, 'schema': getattr(args, 'schema', None) or 'public', 'eventId': EVENT, 'fingerprint': digest(expected), 'integrity': integrity, 'exactFixtureMatch': True}


COLUMNS = {
    'users': 'id,email,password_hash,created_at', 'profiles': 'user_id,display_name,time_zone,version',
    'profile_skills': 'user_id,skill', 'profile_availability': 'user_id,slot_order,day_of_week,start_time,end_time',
    'event_participants': 'hackathon_id,user_id,registered_at',
    'teams': 'id,hackathon_id,name,description,desired_skills,leader_user_id,created_at',
    'team_members': 'id,hackathon_id,team_id,user_id,joined_at',
    'audit_events': 'id,hackathon_id,team_id,actor_id,action,target_type,target_id,occurred_at',
}


def seed(args):
    guard(args)
    password_hash = os.environ.get('BENCHMARK_PASSWORD_HASH', '')
    if not re.fullmatch(r'\$2[aby]\$12\$[./A-Za-z0-9]{53}', password_hash):
        raise ValueError('Supply BENCHMARK_PASSWORD_HASH (BCrypt cost12) using the hash-password command.')
    tables = data(password_hash)
    if psql(args, "SELECT to_regclass('benchmark_metadata') IS NOT NULL;") == 't':
        return verify(args, tables)  # repeat is a verified no-op, never an overwrite
    checks = ' + '.join(f'(SELECT count(*) FROM {table})' for table in list(tables) + ['idempotency_records', 'team_invitations', 'join_requests'])
    sql = ["BEGIN; SET LOCAL TIME ZONE 'UTC'; SELECT pg_advisory_xact_lock(8675309);"]
    sql += [f"DO $$ BEGIN IF ({checks})<>0 THEN RAISE EXCEPTION 'Benchmark target must be empty'; END IF; END $$;"]
    sql += ["CREATE TABLE benchmark_metadata(singleton BOOLEAN PRIMARY KEY CHECK(singleton),version TEXT NOT NULL,fingerprint CHAR(64) NOT NULL);"]
    sql += [f"INSERT INTO hackathons(id,name,minimum_team_size,maximum_team_size,active,created_at) VALUES ({literal(EVENT)},'TeamForge Benchmark',2,5,true,{literal(STAMP)});"]
    for table, rows in tables.items():
        # The application's triggers, uniqueness and deferred leader FK stay enabled.
        values = ',\n'.join('(' + ','.join(literal(v) for v in row) + ')' for row in rows)
        sql += [f'INSERT INTO {table}({COLUMNS[table]}) VALUES {values};']
    sql += [f"INSERT INTO benchmark_metadata VALUES(true,{literal(VERSION)},{literal(digest(tables))});", 'SET CONSTRAINTS ALL IMMEDIATE; COMMIT;', 'ANALYZE;']
    psql(args, '\n'.join(sql))
    return verify(args, tables)


def hash_password(args):
    # Reuse dependencies already packaged by the application; install nothing.
    with tempfile.TemporaryDirectory(prefix='teamforge-benchmark-') as directory, zipfile.ZipFile(args.jar) as jar:
        libraries = [name for name in jar.namelist() if re.search(r'/spring-(security-crypto|jcl)-[^/]+\.jar$', name)]
        if len(libraries) != 2:
            raise ValueError('Build the executable backend JAR first.')
        paths = []
        for name in libraries:
            path = Path(directory)/Path(name).name
            path.write_bytes(jar.read(name)); paths.append(str(path))
        result = subprocess.run([args.java, '-cp', os.pathsep.join(paths), str(Path(__file__).with_name('HashPassword.java'))], capture_output=True, text=True)
        if result.returncode:
            raise ValueError('Password hashing failed; require Java21 and a synthetic BENCHMARK_PASSWORD of12–72 bytes.')
        print(result.stdout, end='')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['seed', 'verify', 'hash-password'])
    parser.add_argument('--database')
    parser.add_argument('--schema', help='Explicit isolated teamforge_benchmark_* schema; search path has no public fallback')
    parser.add_argument('--confirm-isolated', action='store_true')
    parser.add_argument('--psql', default=shutil.which('psql') or 'psql')
    parser.add_argument('--java', default=shutil.which('java') or 'java')
    parser.add_argument('--jar', default='backend/target/teamforge-0.0.1-SNAPSHOT.jar')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        if args.command == 'hash-password':
            hash_password(args); return
        report = seed(args) if args.command == 'seed' else verify(args)
        output = json.dumps(report, indent=2, sort_keys=True)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(output + '\n', encoding='utf-8')
        print(output)
    except (ValueError, RuntimeError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, str(error) + '\n')


if __name__ == '__main__':
    main()
