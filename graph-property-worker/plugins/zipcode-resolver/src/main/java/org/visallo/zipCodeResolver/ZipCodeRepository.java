package org.visallo.zipCodeResolver;

import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;
import org.visallo.core.exception.VisalloException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZipCodeRepository {
    public static final String ZIPCODE_CSV = "zipcode.csv";
    private final Map<String, ZipCodeEntry> zipCodesByZipCode = new HashMap<>();

    public ZipCodeRepository() {
        try {
            InputStream zipCodeCsv = this.getClass().getResourceAsStream(ZIPCODE_CSV);
            if (zipCodeCsv == null) {
                throw new VisalloException("Could not read zipcode.csv from classpath (try rebuilding): " + this.getClass().getResource("zipcode.csv"));
            }
            InputStreamReader reader = new InputStreamReader(zipCodeCsv);
            CsvListReader csvReader = new CsvListReader(reader, CsvPreference.STANDARD_PREFERENCE);
            csvReader.read(); // skip title line

            List<String> line;
            while ((line = csvReader.read()) != null) {
                if (line.size() < 5) {
                    continue;
                }
                String zipCode = line.get(0);
                String city = line.get(1);
                String state = line.get(2);
                double latitude = Double.parseDouble(line.get(3));
                double longitude = Double.parseDouble(line.get(4));
                zipCodesByZipCode.put(zipCode, new ZipCodeEntry(zipCode, city, state, latitude, longitude));
            }
        } catch (IOException ex) {
            throw new VisalloException("Could not read zipcode.csv", ex);
        }
    }

    public ZipCodeEntry find(String text) {
        return zipCodesByZipCode.get(text.substring(0, Math.min(text.length(), 5)));
    }
}
