import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

public class SpotifyWrapped {

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //Need to first convert to database
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    private static final String WRAP = ""; // Insert Google Sheet link between quotes

    public static void main(String[] args) {
        LocalDateTime currentDateTime = LocalDateTime.now();
        String currentYear = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy"));
        String currentMonth = currentDateTime.format(DateTimeFormatter.ofPattern("MMMM"));

        //Get data from google sheets
        String googleSheetsLink = WRAP;
        String pandasUrl = convertGoogleSheetUrl(googleSheetsLink);

        List<String[]> data = readCsvFromUrl(pandasUrl);

        //Manipulate data from here
        //Manipulate data from here
        //Manipulate data from here
        //0: Date&Time, 1: Song, 2: Artist, 3: ID, 4: Link

        Map<String, Long> artistCounts = data.stream()
                .collect(Collectors.groupingBy(row -> row[1], Collectors.counting()));

        System.out.println("\n");

        List<String[]> wrapped = data.stream()
                .filter(row -> row[0].contains(currentMonth))
                .collect(Collectors.toList());

        printMonthlySongNumbers(data);

        System.out.println("\n");

        Map<String, Long> wrappedArtistCounts = wrapped.stream()
                .collect(Collectors.groupingBy(row -> row[1], Collectors.counting()));

        Map<String, Long> wrappedSongCounts = wrapped.stream()
                .collect(Collectors.groupingBy(row -> row[2], Collectors.counting()));

        System.out.println("I LISTENED TO " + wrappedArtistCounts.size() + " DIFFERENT ARTISTS IN " + currentYear + "\n");
        System.out.println("I LISTENED TO " + wrapped.size() + " SONGS IN " + currentYear + " (ROUGHLY " + (3 * wrapped.size()) + " MINUTES OR " + (3 * wrapped.size() / 60) + " HOURS OR " + (3 * wrapped.size() / 60 / 24) + " DAYS) \n");
        System.out.println("I LISTENED TO " + wrappedSongCounts.size() + " DIFFERENT SONGS IN " + currentYear + "\n");
        System.out.println("_________________________________________________________\n");

        Map<String, Long> mostPopularArtist = wrappedArtistCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 10)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, HashMap::new));

        Map<String, Long> mostPopularSong = wrappedSongCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 15)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, HashMap::new));

        System.out.println("MY TOP TEN ARTISTS ON SPOTIFY OF " + currentYear);
        mostPopularArtist.entrySet().stream().limit(10).forEach(entry -> System.out.println(entry.getValue() + " " + entry.getKey()));

        System.out.println("_________________________________________________________\n");

        System.out.println("MY TOP TEN SONGS ON SPOTIFY OF " + currentYear);
        mostPopularSong.entrySet().stream().limit(10).forEach(entry -> System.out.println(entry.getValue() + " " + entry.getKey()));

        System.out.println("\n");
        System.out.println("_________________________________________________________\n");
        System.out.println("\n");

        long oneTimeArtists = wrappedArtistCounts.values().stream().filter(count -> count == 1).count();
        long oneTimeSongs = wrappedSongCounts.values().stream().filter(count -> count == 1).count();

        System.out.println("I LISTENED TO " + oneTimeArtists + " ARTISTS ONLY ONE TIME IN " + currentYear);
        System.out.println("\n");
        System.out.println("I LISTENED TO " + oneTimeSongs + " SONGS ONLY ONE TIME IN " + currentYear);
        System.out.println("\n");

        long taylorSwiftCount = wrappedArtistCounts.getOrDefault("Taylor Swift", 0L);
        System.out.println("TAYLOR SWIFT COUNT: " + taylorSwiftCount);
        System.out.println("\n");
    }

    private static String convertGoogleSheetUrl(String url) {
        String pattern = "https://docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9-_]+)(/edit#gid=(\\d+)|/edit.*)?";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(url);

        if (matcher.find()) {
            return "https://docs.google.com/spreadsheets/d/" + matcher.group(1) + "/export?" + (matcher.group(3) != null ? "gid=" + matcher.group(3) + "&" : "") + "format=csv";
        }
        return url;
    }

    private static List<String[]> readCsvFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build();
            return csvReader.readAll();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void printMonthlySongNumbers(List<String[]> data) {
        String[] months = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November"};
        for (String month : months) {
            long count = data.stream().filter(row -> row[0].contains(month)).count();
            System.out.println(month.toUpperCase() + " SONG NUMBER: " + count + " (ROUGHLY " + (3 * count / 60) + " HOURS)");
        }
    }
}
