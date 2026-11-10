import unittest
from types import SimpleNamespace
from unittest.mock import patch
import seed


class SeedTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = seed.data('synthetic-fixture-hash')

    def test_exact_counts_unique_ids_and_repeatability(self):
        self.assertEqual(len(self.rows['users']), 10000)
        self.assertEqual(len(self.rows['profiles']), 10000)
        self.assertEqual(len(self.rows['teams']), 2000)
        self.assertEqual(len({row[0] for row in self.rows['users']}), 10000)
        self.assertEqual(seed.digest(self.rows), seed.digest(seed.data('synthetic-fixture-hash')))

    def test_leaders_capacity_and_event_exclusivity(self):
        members = self.rows['team_members']
        self.assertEqual(len({(row[1], row[3]) for row in members}), len(members))
        for team in self.rows['teams']:
            roster = [row for row in members if row[2] == team[0]]
            self.assertTrue(1 <= len(roster) <= 5)
            self.assertIn(team[5], [row[3] for row in roster])
        from collections import Counter
        sizes = Counter(row[2] for row in members)
        self.assertEqual(Counter(sizes.values()), {1: 400, 2: 400, 3: 400, 4: 400, 5: 400})

    def test_normal_databases_and_missing_opt_in_rejected_before_connection(self):
        with patch.object(seed, 'psql') as connection:
            for database, confirm in [('teamforge_db', True), ('postgres', True), ('teamforge_benchmark_m8a', False)]:
                with self.assertRaises(ValueError):
                    seed.guard(SimpleNamespace(database=database, confirm_isolated=confirm))
            connection.assert_not_called()

    def test_different_database_connection_is_rejected(self):
        with patch.object(seed, 'psql', return_value='teamforge_db'):
            with self.assertRaises(ValueError):
                seed.guard(SimpleNamespace(database='teamforge_benchmark_m8a', confirm_isolated=True))

    def test_public_schema_is_rejected_and_validated_schema_has_no_fallback(self):
        with patch.object(seed, 'psql') as connection:
            with self.assertRaises(ValueError):
                seed.guard(SimpleNamespace(database='teamforge_db', schema='public', confirm_isolated=True))
            connection.assert_not_called()
        with patch.object(seed, 'psql', side_effect=['teamforge_db', 'public']):
            with self.assertRaises(ValueError):
                seed.guard(SimpleNamespace(database='teamforge_db', schema='teamforge_benchmark_m8a', confirm_isolated=True))

    def test_valid_synthetic_profiles_and_hash_sensitive_fingerprint(self):
        self.assertTrue(all(row[1].endswith('@example.test') for row in self.rows['users']))
        self.assertEqual(len({row[2] for row in self.rows['profiles']}), 8)
        self.assertTrue(all(row[3] < row[4] for row in self.rows['profile_availability']))
        self.assertNotEqual(seed.digest(self.rows), seed.digest(seed.data('different-fixture-hash')))


if __name__ == '__main__':
    unittest.main()
