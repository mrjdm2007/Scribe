import Foundation

/// Small, bounded expression evaluator for educational graphing. Supports x, constants,
/// arithmetic, powers, parentheses and common one-argument functions.
public enum ScribeMathExpression {
    public static func evaluate(_ source: String, x: Double) -> Double? {
        var parser = Parser(source: source, x: x)
        guard let value = parser.expression(), parser.isAtEnd, value.isFinite, abs(value) < 1.0e12 else { return nil }
        return value
    }

    private struct Parser {
        let tokens: [String]
        let x: Double
        var index = 0
        var isAtEnd: Bool { index >= tokens.count }

        init(source: String, x: Double) {
            self.x = x
            let pattern = #"[A-Za-z]+|(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?|\*\*|[+\-*/^(),]"#
            let regex = try! NSRegularExpression(pattern: pattern)
            let ns = source as NSString
            let matches = regex.matches(in: source, range: NSRange(location: 0, length: ns.length))
            tokens = matches.map { ns.substring(with: $0.range) }
            let consumed = matches.reduce(0) { $0 + $1.range.length }
            let whitespaceOnly = source.replacingOccurrences(of: #"[\s]"#, with: "", options: .regularExpression)
            let tokenText = tokens.joined()
            _ = consumed
            if tokenText.count != whitespaceOnly.count { tokens = [] }
        }

        mutating func expression() -> Double? {
            var value = term()
            while let op = peek, op == "+" || op == "-" {
                index += 1
                guard let rhs = term(), let lhs = value else { return nil }
                value = op == "+" ? lhs + rhs : lhs - rhs
            }
            return value
        }
        mutating func term() -> Double? {
            var value = power()
            while let op = peek, op == "*" || op == "/" {
                index += 1
                guard let rhs = power(), let lhs = value else { return nil }
                if op == "/" && abs(rhs) < 1e-12 { return nil }
                value = op == "*" ? lhs * rhs : lhs / rhs
            }
            return value
        }
        mutating func power() -> Double? {
            guard var value = unary() else { return nil }
            if peek == "^" || peek == "**" {
                index += 1
                guard let rhs = power() else { return nil }
                value = Foundation.pow(value, rhs)
            }
            return value.isFinite ? value : nil
        }
        mutating func unary() -> Double? {
            if peek == "+" { index += 1; return unary() }
            if peek == "-" { index += 1; return unary().map { -$0 } }
            return primary()
        }
        mutating func primary() -> Double? {
            guard let token = peek else { return nil }
            index += 1
            if token == "(" {
                guard let value = expression(), peek == ")" else { return nil }
                index += 1
                return value
            }
            if let number = Double(token) { return number }
            if token == "x" { return x }
            if token == "pi" { return Double.pi }
            if token == "e" { return M_E }
            if token.first?.isLetter == true {
                guard peek == "(" else { return nil }
                index += 1
                guard let argument = expression(), peek == ")" else { return nil }
                index += 1
                switch token.lowercased() {
                case "sin": return sin(argument)
                case "cos": return cos(argument)
                case "tan": return abs(cos(argument)) < 1e-10 ? nil : tan(argument)
                case "sqrt": return argument >= 0 ? sqrt(argument) : nil
                case "abs": return abs(argument)
                case "ln": return argument > 0 ? log(argument) : nil
                case "log": return argument > 0 ? log10(argument) : nil
                case "exp": return exp(argument)
                default: return nil
                }
            }
            return nil
        }
        var peek: String? { isAtEnd ? nil : tokens[index] }
    }
}
