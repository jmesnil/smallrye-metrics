package io.smallrye.metrics.exporters;

import static io.smallrye.metrics.exporters.PrometheusExporter.getPrometheusMetricName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.smallrye.metrics.ExtendedMetadata;
import io.smallrye.metrics.JmxWorker;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.app.CounterImpl;
import io.smallrye.metrics.mbean.MGaugeImpl;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.junit.Assert;
import org.junit.Test;

public class PrometheusExporterTest {

    @Test
    public void testUptimeGaugeUnitConversion() {
        PrometheusExporter exporter = new PrometheusExporter();
        MetricRegistry baseRegistry = MetricRegistries.get(MetricRegistry.Type.BASE);

        Gauge gauge = new MGaugeImpl(JmxWorker.instance(), "java.lang:type=Runtime/Uptime");
        Metadata metadata = new ExtendedMetadata("jvm.uptime", "display name", "description", MetricType.GAUGE, "milliseconds");
        baseRegistry.register(metadata, gauge);

        long actualUptime /* in ms */ = ManagementFactory.getRuntimeMXBean().getUptime();
        double actualUptimeInSeconds = actualUptime / 1000.0;

        StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.BASE, "jvm.uptime");
        assertNotNull(out);

        double valueFromPrometheus = -1;
        for (String line : out.toString().split(System.getProperty("line.separator"))) {
            if (line.startsWith("base:jvm_uptime_seconds")) {
                valueFromPrometheus /* in seconds */ = Double.valueOf(line.substring("base:jvm_uptime_seconds".length()).trim());
            }
        }
        assertTrue(valueFromPrometheus != -1);
        assertTrue(valueFromPrometheus >= actualUptimeInSeconds);
    }

    @Test
    public void testCounterExport() {
        PrometheusExporter exporter = new PrometheusExporter();
        MetricRegistry appReqistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Counter counter = new CounterImpl();
        appReqistry.register("requestCount", counter);
        counter.inc();

        StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "requestCount");
        assertNotNull(out);
        System.out.println("out = " + out);

        List<String> metrics = Arrays.asList(out.toString().split(System.getProperty("line.separator")))
                .stream()
                .filter(line -> !line.startsWith("#"))
                .collect(Collectors.toList());
        assertEquals(1, metrics.size());
        String metric = metrics.get(0);
        assertTrue(metric.startsWith("application:request_count_total"));
    }

    @Test
    public void metricNameConversion() {
        assertEquals("frag3", getPrometheusMetricName("FRAG3"));
        assertEquals("unicast3", getPrometheusMetricName("UNICAST3"));

        assertEquals("foo_bar", getPrometheusMetricName("FOO-BAR"));
        assertEquals("foo_bar", getPrometheusMetricName("FooBAR"));
        assertEquals("foo_bar", getPrometheusMetricName("FooBar"));
    }
}
