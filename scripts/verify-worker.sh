#!/usr/bin/env bash
set -euo pipefail

WORKER="${WORKER_URL:-https://newsbrief-proxy.a0-07d.workers.dev}"
ARTICLE_URL="${1:-}"
IMAGE_URL="${2:-}"

printf '\n[health] %s\n' "$WORKER/health"
curl --fail-with-body --show-error --silent "$WORKER/health"
printf '\n\n[fetch RSS]\n'
curl --fail-with-body --show-error --silent --get "$WORKER/fetch" \
  --data-urlencode 'url=https://news.zerkalo.io/rss/all.rss' \
  --output /tmp/newsbrief-worker-rss.xml
wc -c /tmp/newsbrief-worker-rss.xml
head -c 250 /tmp/newsbrief-worker-rss.xml
printf '\n'

if [ -n "$ARTICLE_URL" ]; then
  printf '\n[article JSON]\n'
  curl --fail-with-body --show-error --silent --get "$WORKER/article" \
    --data-urlencode "url=$ARTICLE_URL" | head -c 1200
  printf '\n'
else
  echo 'To check /article, pass a real article HTTPS URL as first argument.'
fi

if [ -n "$IMAGE_URL" ]; then
  printf '\n[image]\n'
  curl --fail-with-body --show-error --silent --get "$WORKER/image" \
    --data-urlencode "url=$IMAGE_URL" --output /tmp/newsbrief-worker-image
  file /tmp/newsbrief-worker-image
else
  echo 'To check /image, pass a real image HTTPS URL as second argument.'
fi
