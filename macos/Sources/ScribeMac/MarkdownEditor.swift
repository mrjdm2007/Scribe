import SwiftUI
import AppKit

struct MarkdownEditor: NSViewRepresentable {
    @Binding var text: String
    @Binding var selection: NSRange
    let searchQuery: String
    let matchIndex: Int

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeNSView(context: Context) -> NSScrollView {
        let scroll = NSScrollView()
        scroll.hasVerticalScroller = true
        scroll.hasHorizontalScroller = true
        scroll.autohidesScrollers = false
        scroll.drawsBackground = false
        let editor = NSTextView()
        editor.delegate = context.coordinator
        editor.isRichText = false
        editor.isAutomaticQuoteSubstitutionEnabled = false
        editor.isAutomaticDashSubstitutionEnabled = false
        editor.isVerticallyResizable = true
        editor.isHorizontallyResizable = false
        editor.autoresizingMask = [.width]
        editor.textContainer?.widthTracksTextView = true
        editor.textContainerInset = NSSize(width: 10, height: 12)
        editor.font = NSFont.systemFont(ofSize: 15)
        editor.textColor = .labelColor
        editor.backgroundColor = .clear
        editor.string = text
        scroll.documentView = editor
        return scroll
    }

    func updateNSView(_ scroll: NSScrollView, context: Context) {
        guard let editor = scroll.documentView as? NSTextView else { return }
        if editor.string != text { editor.string = text }
        let fullRange = NSRange(location: 0, length: (editor.string as NSString).length)
        editor.textStorage?.removeAttribute(.backgroundColor, range: fullRange)
        let matches = ranges(in: editor.string, query: searchQuery)
        for range in matches {
            editor.textStorage?.addAttribute(.backgroundColor,
                value: NSColor.systemYellow.withAlphaComponent(0.32), range: range)
        }
        if !matches.isEmpty {
            let index = min(max(0, matchIndex), matches.count - 1)
            let signature = searchQuery + "|" + String(index)
            if context.coordinator.lastSearchSignature != signature {
                let selected = matches[index]
                editor.setSelectedRange(selected)
                editor.scrollRangeToVisible(selected)
                context.coordinator.lastSearchSignature = signature
            }
        } else {
            context.coordinator.lastSearchSignature = ""
            let safeLocation = min(max(0, selection.location), (editor.string as NSString).length)
            if editor.selectedRange().location != safeLocation {
                editor.setSelectedRange(NSRange(location: safeLocation, length: 0))
            }
        }
        context.coordinator.parent = self
    }

    private func ranges(in value: String, query: String) -> [NSRange] {
        guard !query.isEmpty else { return [] }
        let source = value as NSString
        var cursor = 0
        var result: [NSRange] = []
        while cursor < source.length {
            let range = source.range(of: query, options: [.caseInsensitive],
                range: NSRange(location: cursor, length: source.length - cursor))
            if range.location == NSNotFound || range.length == 0 { break }
            result.append(range)
            cursor = range.location + range.length
        }
        return result
    }

    final class Coordinator: NSObject, NSTextViewDelegate {
        var parent: MarkdownEditor
        var lastSearchSignature = ""
        init(_ parent: MarkdownEditor) { self.parent = parent }
        func textDidChange(_ notification: Notification) {
            guard let editor = notification.object as? NSTextView else { return }
            parent.text = editor.string
        }
        func textViewDidChangeSelection(_ notification: Notification) {
            guard let editor = notification.object as? NSTextView else { return }
            parent.selection = editor.selectedRange()
        }
    }
}