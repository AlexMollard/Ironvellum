import argparse
import asyncio
import logging
import sys
import warnings

from .contract import Refusal
from .stdio import run
from .storage import LocalFiles


class Arguments(argparse.ArgumentParser):
    def error(self, message):
        self.exit(2, "Expected --context FILE and --outbox DIRECTORY.\n")


def main():
    parser = Arguments(description="Serve one Ironvellum training export over local stdio.", allow_abbrev=False)
    parser.add_argument("--context", required=True)
    parser.add_argument("--outbox", required=True)
    args = parser.parse_args()
    logging.disable(logging.CRITICAL)
    warnings.simplefilter("ignore")
    try:
        with LocalFiles(args.context, args.outbox) as files:
            asyncio.run(run(files))
    except Refusal as error:
        print(str(error), file=sys.stderr)
        return 2
    except (Exception, KeyboardInterrupt):
        print("connector_unavailable", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
