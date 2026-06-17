package lk.manula.thalgampalastockmanager.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.manula.thalgampalastockmanager.R;

public class HomeActivity extends AppCompatActivity {

    private StockAdapter adapter;
    private RecyclerView recyclerView;
    private TextView tvGrandTotal, tvTotalItems, tvAvgPrice;
    private final List<StockItem> stockList = new ArrayList<>();
    private String currentBranchName = "";

    private final ActivityResultLauncher<String> createPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"),
            uri -> {
                if (uri != null) {
                    savePdfToUri(uri, currentBranchName);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvGrandTotal = findViewById(R.id.tvGrandTotal);
        tvTotalItems = findViewById(R.id.tvTotalItems);
        tvAvgPrice = findViewById(R.id.tvAvgPrice);

        recyclerView = findViewById(R.id.recyclerViewStock);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Add initial empty row
        stockList.add(new StockItem(1));

        adapter = new StockAdapter(stockList, this::calculateSummary);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnAddRow).setOnClickListener(v -> {
            StockItem newItem = new StockItem(stockList.size() + 1);
            newItem.shouldRequestFocus = true;
            stockList.add(newItem);
            adapter.notifyItemInserted(stockList.size() - 1);
            recyclerView.scrollToPosition(stockList.size() - 1);
            calculateSummary();
        });

        findViewById(R.id.btnSavePdf).setOnClickListener(v -> {
            boolean hasData = false;
            for (StockItem item : stockList) {
                if (!item.name.isEmpty() || item.qty > 0 || item.unitPrice > 0) {
                    hasData = true;
                    break;
                }
            }
            
            if (!hasData) {
                Toast.makeText(this, "Cannot save an empty report. Please add at least one item with details.", Toast.LENGTH_LONG).show();
                return;
            }
            showBranchNameDialog();
        });

        findViewById(R.id.btnClearAll).setOnClickListener(v -> showClearConfirmation());

        calculateSummary();
    }

    private void showClearConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Items")
                .setMessage("Are you sure you want to clear all inventory items? This cannot be undone.")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    stockList.clear();
                    stockList.add(new StockItem(1)); // Add one empty row back
                    adapter.notifyDataSetChanged();
                    calculateSummary();
                    Toast.makeText(this, "Inventory cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_check_updates) {
            checkForUpdates();
            return true;
        } else if (id == R.id.action_save_pdf) {
            findViewById(R.id.btnSavePdf).performClick();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkForUpdates() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                // TODO: Replace with your actual GitHub repository details
                URL url = new URL("https://api.github.com/repos/Manula-P/Thalgampala-Stock-Manager/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    String latestVersion = json.getString("tag_name");
                    String downloadUrl = json.getString("html_url");

                    String currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    
                    // Simple version comparison
                    if (!latestVersion.replace("v", "").equals(currentVersion.replace("v", ""))) {
                        handler.post(() -> showUpdateDialog(latestVersion, downloadUrl));
                    } else {
                        handler.post(() -> Toast.makeText(HomeActivity.this, "App is up to date", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    handler.post(() -> Toast.makeText(HomeActivity.this, "Failed to check for updates", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> Toast.makeText(HomeActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                executor.shutdown();
            }
        });
    }

    private void showUpdateDialog(String newVersion, String downloadUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Update Available")
                .setMessage("A new version (" + newVersion + ") is available. Do you want to update?")
                .setPositiveButton("Update", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void showBranchNameDialog() {
        final EditText etBranch = new EditText(this);
        etBranch.setHint("Enter Branch Name (e.g. Colombo)");
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Branch Name")
                .setMessage("Enter the branch name for this report:")
                .setView(etBranch)
                .setPositiveButton("Save", (d, which) -> {
                    currentBranchName = etBranch.getText().toString().trim();
                    String fileName = "Stock_Report_";
                    if (!currentBranchName.isEmpty()) {
                        fileName += currentBranchName.replaceAll("[^a-zA-Z0-9.-]", "_") + "_";
                    }
                    fileName += System.currentTimeMillis() + ".pdf";
                    createPdfLauncher.launch(fileName);
                })
                .setNegativeButton("Cancel", null)
                .create();

        // Add padding to the EditText inside the dialog
        dialog.setView(etBranch, padding, padding, padding, 0);
        dialog.show();
    }

    private void calculateSummary() {
        double total = 0;
        int itemsCount = 0;
        for (int i = 0; i < stockList.size(); i++) {
            StockItem item = stockList.get(i);
            item.id = i + 1; // Update ID based on position
            if (!item.name.isEmpty() || item.qty > 0 || item.unitPrice > 0) {
                total += item.totalPrice;
                itemsCount++;
            }
        }
        // Removed notifyDataSetChanged() from here as it causes cursor jumps
        tvGrandTotal.setText(String.format(Locale.US, "%.2f LKR", total));
        tvTotalItems.setText(String.valueOf(itemsCount));
        tvAvgPrice.setText(String.format(Locale.US, "%.2f", itemsCount == 0 ? 0 : total / itemsCount));
    }

    private void savePdfToUri(Uri uri, String branchName) {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        int x = 40;
        int y = 40;

        // Logo
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.logo_main);
        if (bitmap != null) {
            int logoWidth = 140;
            int logoHeight = 80;
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, logoWidth, logoHeight, false);
            canvas.drawBitmap(scaledBitmap, (595 - logoWidth) / 2f, y, paint);
            y += logoHeight + 20;
        }

        // Header
        paint.setTextSize(24);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Thalgampala Thunapaha Stock Inventory Report", 595 / 2f, y, paint);
        y += 30;

        paint.setTextAlign(Paint.Align.LEFT); // Reset to left align for other details
        if (branchName != null && !branchName.isEmpty()) {
            paint.setTextSize(16);
            paint.setFakeBoldText(true);
            canvas.drawText("Branch: " + branchName, x, y, paint);
            y += 20;
        }

        paint.setTextSize(12);
        paint.setFakeBoldText(false);
        String currentDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        canvas.drawText("Generated on: " + currentDateTime, x, y, paint);
        y += 30;

        // Table Header
        paint.setFakeBoldText(true);
        canvas.drawText("ID", x, y, paint);
        canvas.drawText("Product Name", x + 40, y, paint);
        canvas.drawText("Qty", x + 300, y, paint);
        canvas.drawText("Unit Price", x + 380, y, paint);
        canvas.drawText("Total", x + 480, y, paint);
        y += 10;
        canvas.drawLine(x, y, 555, y, paint);
        y += 20;

        // Table Content
        paint.setFakeBoldText(false);
        for (StockItem item : stockList) {
            if (item.name.isEmpty() && item.qty == 0 && item.unitPrice == 0) continue;
            
            canvas.drawText(String.valueOf(item.id), x, y, paint);
            canvas.drawText(item.name, x + 40, y, paint);
            canvas.drawText(String.valueOf(item.qty), x + 300, y, paint);
            canvas.drawText(String.format(Locale.US, "%.2f", item.unitPrice), x + 380, y, paint);
            canvas.drawText(String.format(Locale.US, "%.2f", item.totalPrice), x + 480, y, paint);
            y += 20;

            if (y > 800) { // Very basic pagination support (not full)
                document.finishPage(page);
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 60;
            }
        }

        y += 20;
        canvas.drawLine(x, y, 555, y, paint);
        y += 30;
        paint.setFakeBoldText(true);
        canvas.drawText("Grand Total: " + tvGrandTotal.getText().toString(), x + 350, y, paint);

        document.finishPage(page);

        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                document.writeTo(outputStream);
                outputStream.close();
                Toast.makeText(this, "PDF Saved Successfully", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show();
        } finally {
            document.close();
        }
    }

    static class StockItem {
        int id;
        String name = "";
        int qty = 0;
        double unitPrice = 0.0;
        double totalPrice = 0.0;
        boolean shouldRequestFocus = false;

        StockItem(int id) {
            this.id = id;
        }
    }

    static class StockAdapter extends RecyclerView.Adapter<StockAdapter.ViewHolder> {
        private final List<StockItem> items;
        private final Runnable onDataChanged;

        StockAdapter(List<StockItem> items, Runnable onDataChanged) {
            this.items = items;
            this.onDataChanged = onDataChanged;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stock_row, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StockItem item = items.get(position);
            holder.bind(item, position, onDataChanged, items, this);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvId, tvTotalPrice;
            EditText etName, etQty, etUnitPrice;
            View btnRemove;
            TextWatcher nameWatcher, qtyWatcher, priceWatcher;

            ViewHolder(View itemView) {
                super(itemView);
                tvId = itemView.findViewById(R.id.tvId);
                tvTotalPrice = itemView.findViewById(R.id.tvTotalPrice);
                etName = itemView.findViewById(R.id.etProductName);
                etQty = itemView.findViewById(R.id.etQty);
                etUnitPrice = itemView.findViewById(R.id.etUnitPrice);
                btnRemove = itemView.findViewById(R.id.btnRemoveItem);
            }

            void bind(StockItem item, int position, Runnable onDataChanged, List<StockItem> items, StockAdapter adapter) {
                // Remove old watchers to avoid recursion and multiple listeners
                if (nameWatcher != null) etName.removeTextChangedListener(nameWatcher);
                if (qtyWatcher != null) etQty.removeTextChangedListener(qtyWatcher);
                if (priceWatcher != null) etUnitPrice.removeTextChangedListener(priceWatcher);

                tvId.setText(String.valueOf(item.id));
                etName.setText(item.name);
                etQty.setText(item.qty == 0 ? "" : String.valueOf(item.qty));
                etUnitPrice.setText(item.unitPrice == 0.0 ? "" : String.format(Locale.US, "%.2f", item.unitPrice));
                tvTotalPrice.setText(String.format(Locale.US, "%.2f", item.totalPrice));

                if (item.shouldRequestFocus) {
                    etName.requestFocus();
                    item.shouldRequestFocus = false;
                }

                btnRemove.setOnClickListener(v -> {
                    if (items.size() > 1) {
                        items.remove(position);
                        onDataChanged.run();
                        adapter.notifyDataSetChanged(); // Refresh IDs after removal
                    } else {
                        // If it's the last item, just clear it instead of removing
                        item.name = "";
                        item.qty = 0;
                        item.unitPrice = 0.0;
                        item.totalPrice = 0.0;
                        onDataChanged.run();
                        adapter.notifyDataSetChanged(); // Refresh visual state
                    }
                });

                nameWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) {
                        item.name = s.toString();
                    }
                };

                qtyWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) {
                        try {
                            String qStr = s.toString();
                            item.qty = qStr.isEmpty() ? 0 : Integer.parseInt(qStr);
                            item.totalPrice = item.qty * item.unitPrice;
                            tvTotalPrice.setText(String.format(Locale.US, "%.2f", item.totalPrice));
                            onDataChanged.run();
                        } catch (NumberFormatException ignored) {}
                    }
                };

                priceWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) {
                        try {
                            String pStr = s.toString();
                            item.unitPrice = pStr.isEmpty() ? 0.0 : Double.parseDouble(pStr);
                            item.totalPrice = item.qty * item.unitPrice;
                            tvTotalPrice.setText(String.format(Locale.US, "%.2f", item.totalPrice));
                            onDataChanged.run();
                        } catch (NumberFormatException ignored) {}
                    }
                };

                etName.addTextChangedListener(nameWatcher);
                etQty.addTextChangedListener(qtyWatcher);
                etUnitPrice.addTextChangedListener(priceWatcher);
            }
        }
    }
}