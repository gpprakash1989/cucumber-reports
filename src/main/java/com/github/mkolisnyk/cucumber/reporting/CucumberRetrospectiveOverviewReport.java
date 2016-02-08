package com.github.mkolisnyk.cucumber.reporting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.cedarsoftware.util.io.JsonReader;
import com.github.mkolisnyk.cucumber.reporting.types.breakdown.BreakdownStats;
import com.github.mkolisnyk.cucumber.reporting.types.result.CucumberFeatureResult;
import com.github.mkolisnyk.cucumber.reporting.types.retrospective.RetrospectiveBatch;
import com.github.mkolisnyk.cucumber.reporting.types.retrospective.RetrospectiveModel;

public class CucumberRetrospectiveOverviewReport extends CucumberResultsCommon {

    @Override
    public int[][] getStatuses(CucumberFeatureResult[] results) {
        // TODO Auto-generated method stub
        return null;
    }
    protected String getReportBase() throws IOException {
        InputStream is = this.getClass().getResourceAsStream("/consolidated-tmpl.html");
        String result = IOUtils.toString(is);
        return result;
    }
    private String[] getFileNames(String rootFolder) throws Exception {
        String[] fileNames = {};
        for (File file : (new File(rootFolder)).listFiles()) {
            if (file.isDirectory()) {
                fileNames = (String[]) ArrayUtils.addAll(fileNames, getFileNames(file.getAbsolutePath()));
            } else {
                fileNames = (String[]) ArrayUtils.add(fileNames, file.getAbsolutePath());
            }
        }
        return fileNames;
    }
    private String[] getFilesByMask(String mask) throws Exception {
        String[] result = {};
        String[] input = getFileNames(".");
        for (String fileName : input) {
            if (fileName.matches(mask)) {
                result = (String[]) ArrayUtils.add(result, fileName);
            }
        }
        return result;
    }
    private BreakdownStats[] calculateStats(String[] files) throws Exception {
        BreakdownStats[] result = {};
        for (String file : files) {
            BreakdownStats stat = new BreakdownStats();
            CucumberFeatureResult[] features = this.readFileContent(file, true);
            for (CucumberFeatureResult feature : features) {
                feature.valuate();
                stat.addPassed(feature.getPassed());
                stat.addFailed(feature.getFailed());
                stat.addSkipped(feature.getSkipped() + feature.getUndefined());
            }
            result = (BreakdownStats[]) ArrayUtils.add(result, stat);
        }
        return result;
    }
    private String drawBarChart(RetrospectiveModel model, BreakdownStats stats, int offset, int barSize) {
        double total = stats.getFailed() + stats.getPassed() + stats.getSkipped();
        final double scale = 0.9;
        if (total > 0) {
            int passedRatio = (int) (scale * model.getHeight() * ((double) stats.getPassed() / total));
            int failedRatio = (int) (scale * model.getHeight() * ((double) stats.getFailed() / total));
            int skippedRatio = (int) (scale * model.getHeight() * ((double) stats.getSkipped() / total));
            String content = String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\""
                            + " stroke=\"black\" stroke-width=\"1\" fill=\"silver\"></rect>"
                        + "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\""
                            + " stroke=\"red\" stroke-width=\"1\" fill=\"red\"></rect>"
                        + "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\""
                            + " stroke=\"silver\" stroke-width=\"1\" fill=\"green\"></rect>",
                    (int) (offset * scale * barSize), 0, (int) (scale * barSize), skippedRatio,
                    (int) (offset * scale * barSize), skippedRatio, (int) (scale * barSize), failedRatio,
                    (int) (offset * scale * barSize), failedRatio + skippedRatio, (int) (scale * barSize), passedRatio
             );
            // Right scale
            final int scaleTicksCount = 5;
            final int maxScaleValue = 100;
            final int vOffset = 10;
            for (int i = 0; i <= scaleTicksCount; i++) {
                content = content.concat(
                    String.format("<text x=\"%d\" y=\"%d\" font-size=\"12\">%d%%</text>",
                       (int) (model.getWidth() * scale),
                       (int) (i * scale * model.getHeight() / scaleTicksCount) + vOffset,
                       maxScaleValue - i * maxScaleValue / scaleTicksCount)
                );
                content = content.concat(
                    String.format("<line stroke-dasharray=\"10,10\""
                        + " x1=\"0\" y1=\"%d\" x2=\"%d\" y2=\"%d\" style=\"stroke:darkgray;stroke-width:1\" />",
                        (int) (i * scale * model.getHeight() / scaleTicksCount),
                        (int) (model.getWidth() * scale),
                        (int) (i * scale * model.getHeight() / scaleTicksCount)
                    )
                );
            }
            // Bottom scale
            content = content.concat(
                String.format("<text x=\"%d\" y=\"%d\" font-size=\"12\">%d</text>",
                    (int) (offset * scale * barSize) + barSize / 2,
                    (int) (scale * model.getHeight()) + vOffset,
                    offset + 1
                )
            );
            return content;
        }
        return "";
    }
    private String drawGraph(RetrospectiveModel model, BreakdownStats[] stats) {
        final double scale = 0.9;
        String content = String.format("<svg xmlns=\"http://www.w3.org/2000/svg\""
                + " version=\"1.1\" width=\"%d\" height=\"%d\">", model.getWidth(), model.getHeight());
        int offset = 0;
        final int barSize = model.getWidth() / stats.length;
        for (BreakdownStats stat : stats) {
            content = content.concat(this.drawBarChart(model, stat, offset, barSize));
            offset++;
        }
        content = content + "</svg>";
        return content;
    }
    private String generateRetrospectiveReport(RetrospectiveModel model, BreakdownStats[] stats) throws Exception {
        String result = getReportBase();
        result = result.replaceAll("__TITLE__", model.getTitle());
        if (model.getRefreshTimeout() > 0 && StringUtils.isNotBlank(model.getRedirectTo())) {
            String refreshHeader
                = String.format("<meta http-equiv=\"Refresh\" content=\"%d; url=%s\" />",
                        model.getRefreshTimeout(), model.getRedirectTo());
            result = result.replaceAll("__REFRESH__", refreshHeader);
        } else {
            result = result.replaceAll("__REFRESH__", "");
        }
        String reportContent = "<h1>" + model.getTitle() + "</h1>" + drawGraph(model, stats);
        reportContent = this.replaceHtmlEntitiesWithCodes(reportContent);
        reportContent = reportContent.replaceAll("[$]", "&#36;");
        result = result.replaceAll("__REPORT__", reportContent);
        return result;
    }
    public void executeReport(RetrospectiveModel model, boolean aggregate, boolean toPDF) throws Exception {
        String[] files = getFilesByMask(model.getMask());
        BreakdownStats[] stats = calculateStats(files);
        File outFile = new File(
                this.getOutputDirectory() + File.separator + this.getOutputName()
                + "-" + model.getReportSuffix() + ".html");
        FileUtils.writeStringToFile(outFile, generateRetrospectiveReport(model, stats));
        if (toPDF) {
            this.exportToPDF(outFile, model.getReportSuffix());
        }
    }
    public void executeReport(RetrospectiveBatch batch, boolean aggregate, boolean toPDF) throws Exception {
        for (RetrospectiveModel model : batch.getModels()) {
            this.executeReport(model, aggregate, toPDF);
        }
    }
    public void executeReport(File config, boolean aggregate, boolean toPDF) throws Exception {
        RetrospectiveBatch batch = (RetrospectiveBatch) JsonReader.jsonToJava(
                FileUtils.readFileToString(config));
        this.executeReport(batch, aggregate, toPDF);
    }
}
