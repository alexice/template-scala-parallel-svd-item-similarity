"""
Import sample data for complementary purchase engine
"""

import predictionio
import argparse
import random
import csv
import re

SEED = 12
NUMSAMPLES = 1000


def prepare_data():
    random.seed(SEED)
    res = []
    with open("./sample_actors.txt") as f:
        actors = [actor for actor in f.read().split("\n") if actor != ""]
    f.close()

    with open("./sample_producers.txt") as f:
        producers = [producer for producer in f.read().split("\n")
                     if producer != ""]
    f.close()

    with open("./sample_directors.txt") as f:
        directors = [director for director in f.read().split("\n")
                     if director != ""]
    f.close()

    with open("./sample_movies.csv", "rb") as csvfile:
        reader = csv.reader(csvfile, delimiter=',')
        for row in reader:
            data = {}
            title = row[1]
            genres = row[2].split("|")[0]
            data["unidadeDeMedida"] = genres
            data["linhaDeProdutoDescricao"] = title
            data["id"] = row[0]

            data["familiaDescricao"] = random.sample(producers, 1).pop()
            data["empresaDescricao"] = random.sample(directors, 1).pop()
            data["descricao"] = random.randrange(72, 180, 1)
            num_actors = random.randrange(7, 25, 1)
            data["actors"] = random.sample(actors, num_actors)
            res.append(data)
    f.close()
    return res


def import_events(client, data):
    count = 0

    for el in data[0:NUMSAMPLES]:
        count += 1
        print("%d / %d" % (count, NUMSAMPLES))
        client.create_event(
            event="$set",
            entity_type="item",
            entity_id=el["id"],
            properties=el)

    print("%s events are imported." % count)


def main():
    parser = argparse.ArgumentParser(
        description="Import sample data for similar items by attributes engine")
    parser.add_argument('--access_key', default='invald_access_key')
    parser.add_argument('--url', default="http://localhost:7070")

    args = parser.parse_args()
    print(args)

    client = predictionio.EventClient(access_key=args.access_key, url=args.url,
                                      threads=4, qsize=100)

    data = prepare_data()
    import_events(client, data)


if __name__ == '__main__':
    main()
