import SwiftUI

struct RichBlockPreview: View {
    let bodyText: String
    let type: String

    var body: some View {
        Group {
            switch type {
            case "heading":
                Text(markdown(bodyText)).font(.title2.weight(.semibold))
                    .frame(maxWidth: .infinity, alignment: .leading)
            case "code":
                ScrollView(.horizontal) {
                    Text(bodyText).font(.system(.body, design: .monospaced))
                        .frame(maxWidth: .infinity, alignment: .leading).padding(12)
                }.background(.quaternary.opacity(0.35), in: RoundedRectangle(cornerRadius: 10))
            case "equation":
                MathJaxPreview(source: bodyText)
                    .frame(minHeight: 72, maxHeight: 150)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(8)
                    .background(.quaternary.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))
            case "table":
                tablePreview
            case "list":
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(bodyText.split(separator: "\n", omittingEmptySubsequences: false).enumerated()), id: \.offset) { _, line in
                        HStack(alignment: .top, spacing: 8) {
                            Text("•").foregroundStyle(.secondary)
                            Text(String(line).replacingOccurrences(of: "- ", with: "").replacingOccurrences(of: "• ", with: ""))
                        }
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
            case "checklist":
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(bodyText.split(separator: "\n", omittingEmptySubsequences: false).enumerated()), id: \.offset) { _, line in
                        let value = String(line)
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: value.lowercased().hasPrefix("[x]") ? "checkmark.square.fill" : "square")
                                .foregroundStyle(.secondary)
                            Text(value.replacingOccurrences(of: "[ ]", with: "").replacingOccurrences(of: "[x]", with: "", options: .caseInsensitive).trimmingCharacters(in: .whitespaces))
                        }
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
            case "quote":
                Text(markdown(bodyText)).italic().padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(alignment: .leading) { Rectangle().fill(.secondary.opacity(0.5)).frame(width: 3) }
                    .background(.quaternary.opacity(0.2))
            case "link":
                if let url = URL(string: bodyText), ["http","https"].contains(url.scheme?.lowercased() ?? "") {
                    Link(bodyText, destination: url)
                } else { Text(bodyText).foregroundStyle(.secondary) }
            case "flashcard":
                let pair = bodyText.components(separatedBy: "\n---ANSWER---\n")
                VStack(alignment: .leading, spacing: 8) {
                    Text("FRONT").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Text(pair.first ?? bodyText)
                    if pair.count > 1 {
                        Divider()
                        Text("BACK").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        Text(pair[1])
                    }
                }.padding(12).background(.quaternary.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))
            default:
                Text(markdown(bodyText)).frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .textSelection(.enabled)
    }

    private var tablePreview: some View {
        let rows = bodyText.split(separator: "\n", omittingEmptySubsequences: false)
            .map { $0.split(separator: "\t", omittingEmptySubsequences: false).map(String.init) }
        return ScrollView(.horizontal) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(rows.indices, id: \.self) { row in
                    HStack(spacing: 0) {
                        ForEach(rows[row].indices, id: \.self) { column in
                            Text(rows[row][column].isEmpty ? " " : rows[row][column])
                                .font(row == 0 ? .caption.weight(.semibold) : .body)
                                .frame(minWidth: 110, alignment: .leading).padding(8)
                                .overlay(Rectangle().stroke(.secondary.opacity(0.2), lineWidth: 0.5))
                        }
                    }
                }
            }
        }
    }

    private func markdown(_ source: String) -> AttributedString {
        (try? AttributedString(markdown: source)) ?? AttributedString(source)
    }
}
