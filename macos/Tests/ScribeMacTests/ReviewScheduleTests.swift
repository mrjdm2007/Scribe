import XCTest
@testable import ScribeCore

final class ReviewScheduleTests: XCTestCase {
    func testAgainResetsToOneDay() { XCTAssertEqual(nextReviewIntervalDays(30, rating: 0), 1) }
    func testHardIncreasesGently() { XCTAssertEqual(nextReviewIntervalDays(10, rating: 1), 12) }
    func testGoodDoubles() { XCTAssertEqual(nextReviewIntervalDays(10, rating: 2), 20) }
    func testEasyTriples() { XCTAssertEqual(nextReviewIntervalDays(10, rating: 3), 30) }
    func testMaximumIntervalIsCapped() { XCTAssertEqual(nextReviewIntervalDays(200, rating: 3), 365) }
    func testInvalidRatingPreservesInterval() { XCTAssertEqual(nextReviewIntervalDays(7, rating: 9), 7) }

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
}
