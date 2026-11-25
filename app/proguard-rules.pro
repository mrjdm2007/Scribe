# PDFBox's JPEG2000 filter is optional; PDFs without JPX images remain fully supported.
# Keep R8 from failing the release build when the optional Gemalto JP2 codec is absent.
-dontwarn com.gemalto.jp2.**
