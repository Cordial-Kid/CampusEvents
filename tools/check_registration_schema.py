"""Portable SQLite integrity checks using C's actual production DDL (no Android SDK).
Business eligibility, lifecycle and queue behavior require Android tests.
"""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "feature/registration/src/main/java/com/example/campusevents/registration/RegistrationModule.java").read_text(encoding="utf-8")
DDL = re.findall(r'db.execSQL\("(CREATE [^"\n]+)"\)', SOURCE)

class RegistrationSchemaTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.executescript("CREATE TABLE users(id INTEGER PRIMARY KEY); INSERT INTO users VALUES(2),(3); CREATE TABLE events(id INTEGER PRIMARY KEY); INSERT INTO events VALUES(1);")
        for sql in sorted(DDL, key=lambda sql: "reg_attendance" in sql):
            self.db.execute(sql)
        self.db.execute("INSERT INTO registrations(userId,eventId,status,cancelReason,registeredAt) VALUES(2,1,'REGISTERED','NONE',100)")
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def test_duplicate_student_event_rejected(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO registrations(userId,eventId,status,cancelReason,registeredAt) VALUES(2,1,'REGISTERED','NONE',200)")
        self.assertEqual(1, self.db.execute("SELECT count(*) FROM registrations").fetchone()[0])

    def test_cancel_state_requires_reason_and_time(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE registrations SET status='CANCELLED'")
        self.db.execute("UPDATE registrations SET status='CANCELLED',cancelReason='USER_CANCELLED',cancelledAt=200")
        self.db.execute("UPDATE registrations SET status='REGISTERED',cancelReason='NONE',cancelledAt=NULL,registeredAt=300")
        self.assertEqual((1,300), self.db.execute("SELECT count(*),registeredAt FROM registrations").fetchone())

    def test_attendance_is_unique_and_belongs_to_registration(self):
        self.db.execute("INSERT INTO reg_attendance(registrationId,checkedInAt) VALUES(1,400)")
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO reg_attendance(registrationId,checkedInAt) VALUES(1,500)")
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO reg_attendance(registrationId,checkedInAt) VALUES(999,500)")

    def test_feedback_constraints_and_edit(self):
        self.db.execute("INSERT INTO reg_attendance(registrationId,checkedInAt) VALUES(1,400)")
        for rating,text in [(0,"bad"),(6,"bad"),(5,"x"*501)]:
            with self.assertRaises(sqlite3.IntegrityError):
                self.db.execute("UPDATE reg_attendance SET rating=?,feedback=?,submittedAt=500",(rating,text))
        self.db.execute("UPDATE reg_attendance SET rating=5,feedback='good',submittedAt=500")
        self.db.execute("UPDATE reg_attendance SET rating=4,feedback='edited',submittedAt=600")
        self.assertEqual((4,"edited"),self.db.execute("SELECT rating,feedback FROM reg_attendance").fetchone())

    def test_event_cancel_rollback_preserves_counts(self):
        self.db.execute("BEGIN")
        self.db.execute("UPDATE registrations SET status='CANCELLED',cancelReason='EVENT_CANCELLED',cancelledAt=500")
        self.db.rollback()
        self.assertEqual(1,self.db.execute("SELECT count(*) FROM registrations WHERE status='REGISTERED'").fetchone()[0])

    def test_v1_to_v2_addition_preserves_rows(self):
        self.db.execute("DROP TABLE reg_attendance")
        before=self.db.execute("SELECT * FROM registrations").fetchall()
        self.db.execute(next(sql for sql in DDL if sql.startswith("CREATE TABLE reg_attendance")))
        self.assertEqual(before,self.db.execute("SELECT * FROM registrations").fetchall())
        self.assertEqual([],self.db.execute("PRAGMA foreign_key_check").fetchall())

if __name__ == "__main__":
    unittest.main(verbosity=2)
