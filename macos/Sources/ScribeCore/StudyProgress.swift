import Foundation

/// Local-calendar study summaries derived from completed focus sessions.
public enum ScribeStudyProgress {
    private static func minutes(_ session: ScribeItem) -> Int {
        guard let range = session.tags.range(of: #"(?:^|;)minutes:(\d+)(?:;|$)"#, options: .regularExpression) else { return 0 }
        let match = String(session.tags[range])
        guard let separator = match.range(of: "minutes:") else { return 0 }
        return max(0, Int(match[separator.upperBound...].trimmingCharacters(in: CharacterSet(charactersIn: ";"))) ?? 0)
    }

    public static func minutesStudied(on day: Date, sessions: [ScribeItem], calendar: Calendar = .current) -> Int {
        sessions.filter {
            $0.kind == "study_session" && $0.deletedAt == nil && minutes($0) > 0 &&
            calendar.isDate(Date(timeIntervalSince1970: Double($0.createdAt) / 1000), inSameDayAs: day)
        }.reduce(0) { $0 + minutes($1) }
    }

    public static func currentStreakDays(sessions: [ScribeItem], today: Date = Date(), calendar: Calendar = .current) -> Int {
        let days = Set(sessions.compactMap { session -> Date? in
            guard session.kind == "study_session", session.deletedAt == nil, minutes(session) > 0 else { return nil }
            return calendar.startOfDay(for: Date(timeIntervalSince1970: Double(session.createdAt) / 1000))
        })
        let start = calendar.startOfDay(for: today)
        let yesterday = calendar.date(byAdding: .day, value: -1, to: start)!
        var cursor: Date
        if days.contains(start) { cursor = start }
        else if days.contains(yesterday) { cursor = yesterday }
        else { return 0 }
        var streak = 0
        while days.contains(cursor) {
            streak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = previous
        }
        return streak
    }
}
