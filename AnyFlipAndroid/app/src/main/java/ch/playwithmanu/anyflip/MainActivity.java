package ch.playwithmanu.anyflip;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import java.util.*;

public class MainActivity extends Activity {
  private EditText urlBox;
  private Button go;
  private TextView status;
  private File generatedPdf;
  private static final int SAVE_REQUEST = 105;
  private static final int MAX_PAGES = 1000;
  private static final int MAX_IMAGE_BYTES = 18 * 1024 * 1024;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(36, 60, 36, 24);
    root.setBackgroundColor(Color.WHITE);
    TextView heading = new TextView(this);
    heading.setText("AnyFlip → PDF"); heading.setTextSize(26); heading.setTextColor(Color.BLACK);
    root.addView(heading);
    TextView hint = new TextView(this);
    hint.setText("Inserisci il link di un libro AnyFlip per cui è consentito il download PDF.");
    root.addView(hint);
    urlBox = new EditText(this); urlBox.setSingleLine(true);
    urlBox.setHint("https://anyflip.com/abcd/efgh/");
    urlBox.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    root.addView(urlBox);
    go = new Button(this); go.setText("Crea PDF"); root.addView(go);
    status = new TextView(this); status.setText("Pronto"); status.setTextColor(Color.DKGRAY);
    root.addView(status);
    TextView privacy = new TextView(this);
    privacy.setText("Nessun account. Nessuna pubblicità. Le richieste vanno direttamente ad AnyFlip.");
    root.addView(privacy);
    setContentView(root);
    go.setOnClickListener(v -> {
      String input = urlBox.getText().toString().trim();
      go.setEnabled(false);
      new Thread(() -> {
        try { generatedPdf = convert(input); onUi(() -> chooseSave()); }
        catch (Exception e) { onUi(() -> { status.setText("Errore: " + e.getMessage()); go.setEnabled(true); }); }
      }).start();
    });
  }

  private void onUi(Runnable callback) { runOnUiThread(callback); }
  private void update(String msg) { onUi(() -> status.setText(msg)); }

  private static String group(String text, String pattern) throws IOException {
    Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text);
    if (!m.find()) throw new IOException("Configurazione del libro non riconosciuta");
    return m.group(1);
  }

  private static byte[] fetch(String url, int limit) throws IOException {
    URL target = new URL(url);
    if (!"https".equals(target.getProtocol()) || !"online.anyflip.com".equals(target.getHost())) throw new IOException("Dominio non autorizzato");
    HttpURLConnection con = (HttpURLConnection) target.openConnection();
    con.setInstanceFollowRedirects(false);
    con.setConnectTimeout(15000); con.setReadTimeout(25000);
    con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
    con.setRequestProperty("Referer", "https://online.anyflip.com/");
    con.setRequestProperty("Accept", "*/*");
    try {
      if (con.getResponseCode() != 200) {
        String location = target.getPath();
        throw new IOException("HTTP " + con.getResponseCode() + " su " + location);
      }
      try (InputStream in = con.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] b = new byte[8192]; int n; int total = 0;
        while ((n = in.read(b)) != -1) {
          total += n; if (total > limit) throw new IOException("File troppo grande"); out.write(b, 0, n);
        }
        return out.toByteArray();
      }
    } finally { con.disconnect(); }
  }

  private File convert(String input) throws Exception {
    URI uri = new URI(input);
    String host = uri.getHost();
    if (!"https".equalsIgnoreCase(uri.getScheme()) ||
       !("anyflip.com".equalsIgnoreCase(host) || "www.anyflip.com".equalsIgnoreCase(host) || "online.anyflip.com".equalsIgnoreCase(host)))
      throw new IOException("Utilizza un URL HTTPS di anyflip.com");
    String[] parts = uri.getPath().split("/");
    if (parts.length < 3 || !parts[1].matches("[A-Za-z0-9_-]+") || !parts[2].matches("[A-Za-z0-9_-]+"))
      throw new IOException("Link del libro non valido");
    String base = "https://online.anyflip.com/" + parts[1] + "/" + parts[2] + "/";
    update("Lettura configurazione…");
    String js = new String(fetch(base + "mobile/javascript/config.js", 3 * 1024 * 1024), StandardCharsets.UTF_8);
    int count = Integer.parseInt(group(js, "(?:\\\"?(?:bookConfig\\.)?(?:total)?[Pp]ageCount\\\"?)\\s*[=:]\\s*\\\"?(\\d+)"));
    if (count < 1 || count > MAX_PAGES) throw new IOException("Numero di pagine non supportato: " + count);
    ArrayList<String> names = new ArrayList<>();
    Matcher m = Pattern.compile("\\\"n\\\"\\s*:\\s*\\[\\s*\\\"([^\\\"]+)\\\"\\s*\\]").matcher(js);
    while (m.find()) names.add(m.group(1));
    boolean useNames = names.size() >= count;
    File out = new File(getCacheDir(), "anyflip-result.pdf");
    if (out.exists() && !out.delete()) throw new IOException("Impossibile sostituire PDF temporaneo");
    PdfDocument pdf = new PdfDocument();
    boolean completed = false;
    try {
      for (int i = 0; i < count; i++) {
        update("Pagina " + (i + 1) + " di " + count);
        String path;
        if (useNames) {
          String name = names.get(i);
          // Reject unsafe or unsupported remote paths, but try the standard page image instead.
          if (!name.matches("[A-Za-z0-9_./-]+") || name.contains("..") || name.startsWith("/"))
            path = "files/mobile/" + (i + 1) + ".jpg";
          else
            path = "files/large/" + name;
        } else path = "files/mobile/" + (i + 1) + ".jpg";
        byte[] raw;
        try {
          raw = fetch(base + path, MAX_IMAGE_BYTES);
        } catch (IOException firstError) {
          String fallback = "files/mobile/" + (i + 1) + ".jpg";
          if (path.equals(fallback)) throw firstError;
          try {
            raw = fetch(base + fallback, MAX_IMAGE_BYTES);
          } catch (IOException secondError) {
            throw new IOException("Pagina " + (i + 1) + ": " + firstError.getMessage() + " ; alternativa: " + secondError.getMessage());
          }
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
        if (opts.outWidth < 1 || opts.outHeight < 1) throw new IOException("Immagine non valida alla pagina " + (i + 1));
        opts.inJustDecodeBounds = false;
        int sample = 1;
        while (opts.outWidth / sample > 2000 || opts.outHeight / sample > 2600) sample *= 2;
        opts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
        if (bitmap == null) throw new IOException("Errore nella decodifica immagine");
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(w, h, i + 1).create());
        Canvas canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, p);
        pdf.finishPage(page);
        bitmap.recycle();
      }
      update("Generazione PDF…");
      try (FileOutputStream stream = new FileOutputStream(out)) { pdf.writeTo(stream); }
      completed = true;
      return out;
    } finally {
      pdf.close();
      if (!completed) out.delete();
    }
  }

  private void chooseSave() {
    status.setText("PDF pronto. Scegli dove salvarlo.");
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("application/pdf");
    intent.putExtra(Intent.EXTRA_TITLE, "AnyFlip-book.pdf");
    startActivityForResult(intent, SAVE_REQUEST);
  }

  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != SAVE_REQUEST) return;
    if (result == RESULT_OK && data != null && data.getData() != null) {
      try (InputStream in = new FileInputStream(generatedPdf);
           OutputStream out = getContentResolver().openOutputStream(data.getData())) {
        if (out == null) throw new IOException("Destinazione non accessibile");
        byte[] b = new byte[32768]; int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
        status.setText("PDF salvato con successo");
      } catch (IOException ex) { status.setText("Errore salvataggio: " + ex.getMessage()); }
    } else status.setText("Salvataggio annullato");
    if (generatedPdf != null) generatedPdf.delete();
    go.setEnabled(true);
  }
}
