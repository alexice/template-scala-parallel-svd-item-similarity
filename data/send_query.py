"""
Send sample query to prediction engine
"""

import predictionio
import argparse



def main():
    parser = argparse.ArgumentParser(
    description="Ask for similar objects")
    parser.add_argument('--items', default='1,10')
    parser.add_argument('--num', default='5')
    parser.add_argument('--url', default="http://localhost:8000")
    parser.add_argument('--minitemid', default='0')
    
    args = parser.parse_args()
    print(args)

    engine_client = predictionio.EngineClient(url=args.url)
    print engine_client.send_query({"items": args.items.split(","), "num": int(args.num), "minItemID": args.minitemid})

if __name__ == '__main__':
    main()

