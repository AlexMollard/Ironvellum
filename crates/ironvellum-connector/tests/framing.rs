use ironvellum_connector::Frames;
use ironvellum_contracts::wire::MAX_FRAME;
use tokio::io::BufReader;

#[tokio::test]
async fn limit_includes_newline_and_final_value_survives_eof() {
    let mut bytes = vec![b' '; MAX_FRAME - 2];
    bytes.extend_from_slice(b"1\n2");
    let mut frames = Frames::new(BufReader::new(bytes.as_slice()));
    assert_eq!(frames.next().await.unwrap().unwrap(), 1);
    assert_eq!(frames.next().await.unwrap().unwrap(), 2);
    assert!(frames.next().await.unwrap().is_none());
}

#[tokio::test]
async fn oversized_frame_consumes_one_extra_byte_and_ends_the_reader() {
    let data = vec![b' '; 2 * MAX_FRAME];
    let mut cursor = std::io::Cursor::new(data);
    let mut frames = Frames::new(&mut cursor);
    assert_eq!(
        frames.next().await.unwrap_err().kind(),
        std::io::ErrorKind::InvalidData
    );
    assert!(frames.next().await.unwrap().is_none());
    assert_eq!(cursor.position(), MAX_FRAME as u64 + 1);
}
