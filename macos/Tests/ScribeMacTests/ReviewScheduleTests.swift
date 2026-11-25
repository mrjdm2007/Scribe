import XCTest
@testable import ScribeCore

final class ReviewScheduleTests: XCTestCase {
    func testAgainResetsToOneDay() { XCTAssertEqual(nextReviewIntervalDays(30, rating: 0), 1) }
    func testHardIncreasesGently() { XCTAssertEqual(nextReviewIntervalDays(10, rating: 1), 12) }
    func testGoodDoubles() { XCTAssertEqual(nextReviewIntervalDays(10, rating: 2), 20) }
    func testEasyTriples() { XCTAssertEqual(nextReviewIntervalDays(10, rating: 3), 30) }
    func testMaximumIntervalIsCapped() { XCTAssertEqual(nextReviewIntervalDays(200, rating: 3), 365) }
    func testInvalidRatingPreservesInterval() { XCTAssertEqual(nextReviewIntervalDays(7, rating: 9), 7) }

    func testGraphExpressionArithmeticAndPrecedence() {
        XCTAssertEqual(ScribeMathExpression.evaluate("2 + 3 * x", x: 4) ?? .nan, 14, accuracy: 0.000001)
        XCTAssertEqual(ScribeMathExpression.evaluate("(2 + 3)^2", x: 0) ?? .nan, 25, accuracy: 0.000001)
    }
    func testGraphExpressionFunctionsAndConstants() {
        XCTAssertEqual(ScribeMathExpression.evaluate("sin(pi / 2)", x: 0) ?? .nan, 1, accuracy: 0.000001)
        XCTAssertEqual(ScribeMathExpression.evaluate("sqrt(9) + abs(-2)", x: 0) ?? .nan, 5, accuracy: 0.000001)
    }
    func testGraphExpressionRejectsDivisionByZeroAndUnknownTokens() {
        XCTAssertNil(ScribeMathExpression.evaluate("1 / (x - 2)", x: 2))
        XCTAssertNil(ScribeMathExpression.evaluate("system(1)", x: 0))
        XCTAssertNil(ScribeMathExpression.evaluate("x + @", x: 1))
    }

    func testAndroidBackupRecordShapeRoundTrips() throws {
        let json = """
        [{"id":1,"parentId":null,"kind":"topic","title":"ECE","body":"## Notes\\n**Voltage** = IR","uri":"","tags":"","bookmarked":false,"position":0,"createdAt":1,"updatedAt":1,"deletedAt":null,"history":[{"title":"Old","body":"Earlier version","savedAt":123}]}]
        """.data(using: .utf8)!
        let records = try JSONDecoder().decode([BackupRecord].self, from: json)
        XCTAssertEqual(records.first?.title, "ECE")
        XCTAssertEqual(records.first?.body, "## Notes\n**Voltage** = IR")
        XCTAssertEqual(records.first?.history?.first?.body, "Earlier version")
        XCTAssertNil(records.first?.parentId)
        let encoded = try JSONEncoder().encode(records)
        let roundTrip = try JSONDecoder().decode([BackupRecord].self, from: encoded)
        XCTAssertEqual(roundTrip.first?.kind, "topic")
        XCTAssertEqual(roundTrip.first?.history?.first?.savedAt, 123)
    }


    func testDailyStudyGoalSumsOnlySessionsOnTheSelectedDay() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let day = calendar.date(from: DateComponents(year: 2026, month: 11, day: 25))!
        let sessions = [
            ScribeItem(id: 1, kind: "study_session", title: "ECE", tags: "minutes:25",
                createdAt: Int64(calendar.date(byAdding: .hour, value: 9, to: day)!.timeIntervalSince1970 * 1000)),
            ScribeItem(id: 2, kind: "study_session", title: "Math", tags: "minutes:35",
                createdAt: Int64(calendar.date(byAdding: .hour, value: 13, to: day)!.timeIntervalSince1970 * 1000)),
            ScribeItem(id: 3, kind: "study_session", title: "Yesterday", tags: "minutes:60",
                createdAt: Int64(calendar.date(byAdding: .day, value: -1, to: day)!.timeIntervalSince1970 * 1000))
        ]
        XCTAssertEqual(ScribeStudyProgress.minutesStudied(on: day, sessions: sessions, calendar: calendar), 60)
    }

    func testStudyStreakIncludesConsecutiveDaysAndYesterdayStart() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let today = calendar.date(from: DateComponents(year: 2026, month: 11, day: 25))!
        let sessions = stride(from: -1, through: -3, by: -1).map { offset in
            ScribeItem(id: Int64(abs(offset)), kind: "study_session", title: "Study",
                tags: "minutes:25",
                createdAt: Int64(calendar.date(byAdding: .day, value: offset, to: today)!.timeIntervalSince1970 * 1000))
        }
        XCTAssertEqual(ScribeStudyProgress.currentStreakDays(sessions: sessions, today: today, calendar: calendar), 3)
    }

    func testStudyStreakExpiresAfterTwoMissedDays() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let today = calendar.date(from: DateComponents(year: 2026, month: 11, day: 25))!
        let old = calendar.date(byAdding: .day, value: -2, to: today)!
        let session = ScribeItem(id: 1, kind: "study_session", title: "Study", tags: "minutes:25",
            createdAt: Int64(old.timeIntervalSince1970 * 1000))
        XCTAssertEqual(ScribeStudyProgress.currentStreakDays(sessions: [session], today: today, calendar: calendar), 0)
    }

    func testFlashcardReviewTagsPreserveMetadataAndSetSharedDueDate() {
        XCTAssertEqual(flashcardReviewTags("subject:ECE;interval:2;dueAt:1000;custom:x", rating: 2, nowMillis: 10_000), "subject:ECE;custom:x;interval:4;dueAt:345610000;lastReviewedAt:10000")
    }
    func testFlashcardAgainSchedulesOneDayAndHandlesLegacyDecimalIntervals() {
        XCTAssertEqual(flashcardReviewTags("interval:2.5", rating: 0, nowMillis: 0), "interval:1;dueAt:86400000;lastReviewedAt:0")
    }
    func testHugeFlashcardIntervalsAreSafelyCapped() {
        XCTAssertEqual(nextReviewIntervalDays(Int64.max, rating: 3), 365)
    }
    func testTrashRestoreBatchDoesNotRevivePreviouslyDeletedChildren() {
        XCTAssertTrue(belongsToTrashBatch(100_002, rootDeletedAt: 100_000))
        XCTAssertFalse(belongsToTrashBatch(90_000, rootDeletedAt: 100_000))
        XCTAssertFalse(belongsToTrashBatch(nil, rootDeletedAt: 100_000))
    }
    func testDuplicatedItemsDoNotReuseCloudIdentity() {
        XCTAssertEqual(independentCopyTags("subject:ECE;cloud_id:abc-123;priority:high"), "subject:ECE;priority:high")
        XCTAssertEqual(independentCopyTags("cloud_id:abc-123"), "")
    }
    func testBulkTaggingPreservesMetadataAndAvoidsDuplicates() {
        XCTAssertEqual(appendingTag("subject:ECE;bulk:review;priority:high", tag: "exam"),
                       "subject:ECE;bulk:review;priority:high;exam")
        XCTAssertEqual(appendingTag("subject:ECE;bulk:review", tag: "bulk:review"),
                       "subject:ECE;bulk:review")
    }

    func testBulkTaggingSanitizesSeparatorsAndIgnoresBlankTags() {
        XCTAssertEqual(appendingTag("subject:ECE", tag: " revision; notes "),
                       "subject:ECE;revision notes")
        XCTAssertEqual(appendingTag("subject:ECE", tag: "  "), "subject:ECE")
    }

    func testBackupValidationRejectsDuplicateIDsMissingParentsAndCycles() {
        func record(_ id: Int64, _ parent: Int64?) -> BackupRecord {
            BackupRecord(id: id, parentId: parent, kind: "topic", title: "Topic",
                body: "", uri: "", tags: "", bookmarked: false, position: 0,
                createdAt: 1, updatedAt: 1, deletedAt: nil)
        }
        XCTAssertNil(backupValidationError([record(1, nil), record(2, 1)]))
        XCTAssertEqual(backupValidationError([record(1, nil), record(1, nil)]),
                       "Backup contains duplicate item IDs.")
        XCTAssertEqual(backupValidationError([record(2, 1)]),
                       "Backup contains an item with a missing parent.")
        XCTAssertEqual(backupValidationError([record(1, 2), record(2, 1)]),
                       "Backup contains a circular notebook hierarchy.")
    }

    func testSnapshotThrottleAvoidsOneVersionPerTypingPause() {
        XCTAssertTrue(shouldCreateSnapshot(previousBody: "old", nextBody: "new", latestSnapshotAt: 0, now: 100_000))
        XCTAssertFalse(shouldCreateSnapshot(previousBody: "old", nextBody: "newer", latestSnapshotAt: 90_000, now: 100_000))
        XCTAssertTrue(shouldCreateSnapshot(previousBody: "old", nextBody: "newer", latestSnapshotAt: 30_000, now: 100_000))
        XCTAssertFalse(shouldCreateSnapshot(previousBody: "same", nextBody: "same", latestSnapshotAt: 0, now: 100_000))
    }

}
