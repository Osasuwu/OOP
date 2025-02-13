import psycopg2
import csv
from datetime import datetime

# Database connection
conn = psycopg2.connect(
    dbname="MusicPlaylistGenerator",
    user="postgres",
    password="somepassword",
    host="localhost",
    port="5432"
)
cur = conn.cursor()

# Read CSV file
csv_file = "Insert_Your_CSV_Here.csv"

with open(csv_file, "r", encoding="utf-8") as file:
    reader = csv.reader(file)
    
    for row in reader:
        # Extract data
        date_time_str, song_name, artist_name, spotify_id, link = row
        
        # Convert date format
        date_time = datetime.strptime(date_time_str, "%B %d, %Y at %I:%M%p")

        # Insert artist (if not exists)
        cur.execute("SELECT id FROM artists WHERE name = %s;", (artist_name,))
        artist_id = cur.fetchone()
        
        if artist_id is None:
            cur.execute("INSERT INTO artists (name) VALUES (%s) RETURNING id;", (artist_name,))
            artist_id = cur.fetchone()[0]
        else:
            artist_id = artist_id[0]

        # Insert song (if not exists)
        cur.execute("SELECT id FROM songs WHERE spotify_id = %s;", (spotify_id,))
        song_id = cur.fetchone()
        
        if song_id is None:
            cur.execute("""
                INSERT INTO songs (name, artist_id, spotify_id, link) 
                VALUES (%s, %s, %s, %s) RETURNING id;
            """, (song_name, artist_id, spotify_id, link))
            song_id = cur.fetchone()[0]
        else:
            song_id = song_id[0]

        # Insert listening history
        cur.execute("""
            INSERT INTO user_listening_history (user_id, song_id, date_listened, listening_time)
            VALUES (%s, %s, %s);
        """, (1, song_id, date_time))  # Replace 1 with actual user_id(If you have multiple users)

# Commit and close
conn.commit()
cur.close()
conn.close()
print("Data imported successfully!")
