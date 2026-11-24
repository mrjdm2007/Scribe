import XCTest
@testable import ScribeMac

final class ReviewScheduleTests: XCTestCase {
    func testAgainResetsToOneDay() { XCTAssertEqual(nextReviewIntervalDays(30, rating: 0), 1) }
    func testHardIncreasesGently() { XCTAssertEqual(nextReviewIntervalDays(10, rating: 1), 12) }
    func testGoodDoubles() { XCTAssertEqual(nextReviewIntervalDays(10, rating: 2), 20) }
    func testEasyTriples() { XCTAssertEqual(nextReviewIntervalDays(10, rating: 3), 30) }
    func testMaximumIntervalIsCapped() { XCTAssertEqual(nextReviewIntervalDays(200, rating: 3), 365) }
    func testInvalidRatingPreservesInterval() { XCTAssertEqual(nextReviewIntervalDays(7, rating: 9), 7) }
}
